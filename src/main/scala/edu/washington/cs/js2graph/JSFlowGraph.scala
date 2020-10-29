package edu.washington.cs.js2graph

import com.ibm.wala.cast.js.ssa._
import com.ibm.wala.ipa.callgraph.CallGraph
import com.ibm.wala.ipa.cfg.ExplodedInterproceduralCFG
import com.ibm.wala.ssa._
import com.semantic_graph.NodeId
import edu.washington.cs.js2graph.Constants.GraphWriter

import scala.collection.mutable
import scala.jdk.CollectionConverters._

class JSFlowGraph(g: GraphWriter, cg: CallGraph) {
  private val icfg = ExplodedInterproceduralCFG.make(cg)
  private val dataflow = new IFDSDataFlow(icfg)
  private val results = dataflow.solve

  /*
   Super graph is also called exploded CFG.
   Super graph is a call-graph of CGNode, and each CGNode is a CFG of blocks.
   */
  private val superGraph = dataflow.problem.getSupergraph

  private val instructionOpNodeCacheMap = mutable.Map[SSAInstruction, NodeId]()

  /** Each instruction is mapped to at most one unique node
    */
  private def getFromOpNode(instruction: SSAInstruction, apiName: String, attrs: Constants.NodeAttrs): NodeId = {
    if (!instructionOpNodeCacheMap.contains(instruction)) {
      instructionOpNodeCacheMap.addOne(instruction, g.createNode(apiName, attrs))
    }
    instructionOpNodeCacheMap(instruction)
  }

  /** Each instruction is mapped by dataFlow analysis to its expected node label and attributes, and then used for
    * creating a new node if not already existing.
    */
  private def getFromIntermediateOpNode(instruction: SSAInstruction): Set[NodeId] = {
    dataflow.getOpNodeNameAndAttrs(instruction).map { case (name, attrs) => getFromOpNode(instruction, name, attrs) }
  }

  /** Get sink opNodes for a [[SSAInstruction]]. Note that the return "sink" opNode might be used as source to some
    * other node as well, that is, some opNode can be both a sink and a source (i.e. intermediate node).
    *
    * FIXME: defUse and symTable should be an attribute of some processor class
    */
  def getSinkOpNodes(symTable: SymbolTable, instruction: SSAInstruction, dataDeps: Map[AbsPath, Set[AbsVal]]): Set[NodeId] = {
    instruction match {
      case invokeInstruction: JavaScriptInvoke =>
        // Case 1: Data-flow analysis has defined some intermediate API name already (as well as tag), use this directly
        val opNodes = getFromIntermediateOpNode(invokeInstruction)
        if (opNodes.nonEmpty) {
          return opNodes
        }
        // Otherwise -- Case 2: Post-analyze used API here
        if (invokeInstruction.getNumberOfUses >= 2) {
          val dispatchFuncIndex = invokeInstruction.getUse(0)
          val dispatchBaseIndex = invokeInstruction.getUse(1)
          // Focus on the case which can't be handled within IFDS: both base and receiver are non-constant (propagated)
          val receiverFuncNames: Set[String] = dataDeps.get(AbsPath.Local(dispatchFuncIndex)) match {
            case None             => Set()
            case Some(fromValues) => fromValues.collect { case AbsVal.Global(g) => g }
          }
          for (receiverFuncName <- receiverFuncNames) {
            // Base is a global variable
            // FIXME: this part can be lifted out to do a more elegant cross-product with the receiverFuncNames
            dataDeps.get(AbsPath.Local(dispatchBaseIndex)) match {
              case None =>
              case Some(fromValues) =>
                return fromValues.flatMap {
                  case AbsVal.Global(globalBaseName) =>
                    Constants.asLibraryAPIName(globalBaseName, receiverFuncName) match {
                      case Some(apiName) =>
                        Some(
                          g.createNode(apiName, Map(JsNodeAttr.TYPE -> NodeType.Call.toString, JsNodeAttr.TAG -> Tag.Singleton.toString)))
                      case _ => None
                    }
                  case _ => None
                }
            }
          }
        }
        // Otherwise, empty
        Set()
      case propertyWrite: JavaScriptPropertyWrite =>
        // Case 3: Write to an API instance's property
        dataDeps.get(AbsPath.Local(propertyWrite.getObjectRef)) match {
          case Some(fromValues) =>
            return fromValues.flatMap {
              case AbsVal.Instance(apiName, _) =>
                if (symTable.isConstant(propertyWrite.getMemberRef)) {
                  val memberName = symTable.getConstantValue(propertyWrite.getMemberRef).toString
                  Some(
                    g.createNode(
                      apiName + "." + memberName,
                      Map(JsNodeAttr.TYPE -> NodeType.FieldRef.toString, JsNodeAttr.TAG -> Tag.Instance.toString)))
                } else {
                  None
                }
              case _ => None
            }
          case None =>
        }
        Set()
      case binaryOpInstruction: SSABinaryOpInstruction =>
        getFromIntermediateOpNode(binaryOpInstruction).toSet
      case _ => Set()
    }
  }

  private def resolveDataDeps(dataFlowDeps: Map[AbsPath, Set[AbsVal]], absVar: AbsPath): Set[AbsVal] = {
    var allDeps = dataFlowDeps.getOrElse(absVar, Set())
    // FIXME: fixpoint? or do we even need this resolution?
    allDeps = allDeps.flatMap {
      case v @ AbsVal.Global(name) => dataFlowDeps.getOrElse(AbsPath.Global(name), Set(v))
      case v                       => Set(v)
    }
    allDeps
  }

  /** IFDS based data-flow analysis
    */
  def addDataFlowGraph(): Unit = {

    /*
    In JavaScript, both function and class are just object.

    Function calls (i.e. `f()`) becomes invocation of "fake" target method called `do` with argument referring to
    a SSA variable representing the `f`.

    Constructor calls (i.e. `new f()`) becomes invocation of "fake" target method called `ctor`.

    For more: https://sourceforge.net/p/wala/mailman/message/30369613/
     */

    // Scan the method and find invocation of standard APIs
    // Each instruction might correspond to multiple operation nodes (opNodes), since there could be more than one
    // possible base classes (which depends on the result of the DFA).
    // The opNode might have in-flow from constant nodes or other opNode, it might flow to other opNode.
    // Some opNode can have no in-flow or out-flow.
    // The edge is called "dependency edge" (or depEdge)
    for (block <- superGraph.asScala) {
      if (Constants.isApplicationNode(block.getNode)) {
        val symTable = block.getNode.getIR.getSymbolTable
        // Each block corresponds to a _single_ instruction
        val instruction = block.getDelegate.getInstruction
        if (instruction != null) {
          val dataFlowDeps = dataflow.getFacts(results, block)

          for (sinkOpNode <- getSinkOpNodes(symTable, instruction, dataFlowDeps)) {
            // Build semantic graph of opNode and depEdge
            val firstUseIdx = instruction match {
              case _: JavaScriptInvoke        => 1 // from base
              case _: JavaScriptPropertyWrite => 2
              case _                          => 0
            }
            for (iu <- firstUseIdx until instruction.getNumberOfUses) {
              val isInvokeBase = instruction.isInstanceOf[JavaScriptInvoke] && iu == 1
              val use = instruction.getUse(iu)
              // If the use is a constant
              if (symTable.isConstant(use) && symTable.getConstantValue(use) != null) {
                val v = symTable.getConstantValue(use).toString
                try {
                  g.addEdge(
                    g.createNode(v, Map(JsNodeAttr.TYPE -> NodeType.Constant.toString)),
                    sinkOpNode,
                    Map(JsEdgeAttr.TYPE -> EdgeType.DATAFLOW.toString))
                } catch {
                  case e: IllegalArgumentException =>
                    println(e)
                }
              } else {
                for (absVal <- resolveDataDeps(dataFlowDeps, AbsPath.Local(use))) {
                  absVal match {
                    case AbsVal.Global(name) =>
                      // The information of invocation base API is already in the current opNode
                      if (Constants.isLibraryGlobalName(name) && !isInvokeBase) {
                        g.addEdge(
                          g.createNode(name, Map(JsNodeAttr.TAG -> Tag.Singleton.toString)),
                          sinkOpNode,
                          Map(JsEdgeAttr.TYPE -> EdgeType.DATAFLOW.toString))
                      }
                    case AbsVal.Constant(v) =>
                      g.addEdge(
                        g.createNode(v, Map(JsNodeAttr.TYPE -> NodeType.Constant.toString)),
                        sinkOpNode,
                        Map(JsEdgeAttr.TYPE -> EdgeType.DATAFLOW.toString))
                    case AbsVal.Instance(_, sourceInstruction) =>
                      getFromIntermediateOpNode(sourceInstruction).map(fromOpNode =>
                        if (!g.getEdges.contains(fromOpNode, sinkOpNode)) {
                          g.addEdge(fromOpNode, sinkOpNode, Map(JsEdgeAttr.TYPE -> EdgeType.DATAFLOW.toString))
                        })
                    case _ =>
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}
