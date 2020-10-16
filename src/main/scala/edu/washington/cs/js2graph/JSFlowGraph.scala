package edu.washington.cs.js2graph

import java.io.{BufferedWriter, File, FileWriter}
import java.nio.file.Paths

import com.ibm.wala.cast.js.ipa.callgraph.JSCallGraphUtil
import com.ibm.wala.cast.js.nodejs.PatchedNodejsCallGraphBuilderUtil
import com.ibm.wala.cast.js.ssa._
import com.ibm.wala.cast.js.translator.PatchedCAstRhinoTranslatorFactory
import com.ibm.wala.classLoader.IMethod
import com.ibm.wala.ipa.callgraph.{CGNode, CallGraph}
import com.ibm.wala.ipa.cfg.ExplodedInterproceduralCFG
import com.ibm.wala.ssa._
import com.semantic_graph.NodeId
import edu.washington.cs.js2graph.Constants.GW

import scala.collection.mutable
import scala.jdk.CollectionConverters._

object JSFlowGraph {
  private val postApiInvocationNodes = mutable.Map[JavaScriptInvoke, NodeId]()

  def getPostApiInvocationNode(g: GW, invoke: JavaScriptInvoke, apiName: String, attrs: Constants.NodeAttrs): NodeId = {
    if (!postApiInvocationNodes.contains(invoke)) {
      postApiInvocationNodes.addOne(invoke, g.createNode(apiName, attrs))
    }
    postApiInvocationNodes(invoke)
  }

  def getMethodName(s: String): Option[String] = {
    if (s.contains(".js@")) {
      None
    } else {
      if (s.contains("/")) {
        Some(s.split('/').last)
      } else {
        Some(s)
      }
    }
  }

  def getMethodName(node: CGNode): Option[String] = {
    getMethodName(node.getMethod.getDeclaringClass.getName.toString)
  }

  private def splitAtLast(s: String, c: Char): (String, String) = {
    if (s.isEmpty) {
      ("", "")
    } else {
      if (s.head == c) {
        val (heads, rest) = splitAtLast(s.tail, c)
        (c + heads, rest)
      } else {
        ("", s)
      }
    }
  }

  def getAllModuleEntrypoints(jsPath: String): List[String] = {
    val cg = getCallGraph(jsPath)
    val fieldNames = doEntrypointDataFlowAnalysis(cg)
    val ret = mutable.Queue[String]()
    for (fieldName <- fieldNames) {
      ret.addOne("module.exports." + fieldName + "();")
    }
    // FIXME...
    ret.addOne("module.exports();")
    ret.toList
  }

  def writeEntrypoints(jsPath: String, outputPath: String): Unit = {
    val file = new File(outputPath)
    val bw = new BufferedWriter(new FileWriter(file))
    for (line <- getAllModuleEntrypoints(jsPath)) {
      bw.write(line + "\n")
    }
    bw.close()
  }

  /** Add the callgraph to semantic graph
    * @param jsPath Path to the analyzed JS file
    * @return The constructed call-graph (for later use in other analysis)
    */
  def getCallGraph(jsPath: String): CallGraph = {
    val path = Paths.get(jsPath)
    JSCallGraphUtil.setTranslatorFactory(new PatchedCAstRhinoTranslatorFactory)
    val builder = PatchedNodejsCallGraphBuilderUtil.makeCGBuilder(path.toFile)
    val cg = builder.makeCallGraph(builder.getOptions)
    if (Constants.debug.nonEmpty) {
      println(Constants.getIRofCG(cg))
    }
    cg
  }

  def getModuleFieldNames(symTable: SymbolTable, instruction: SSAInstruction, dataDeps: Map[AbsPath, Set[AbsVal]]): Set[String] = {
    instruction match {
      case propertyWrite: JavaScriptPropertyWrite =>
        // Case 3: Write to an API instance's property
        if (symTable.isConstant(propertyWrite.getMemberRef)) {
          val fieldName = symTable.getConstantValue(propertyWrite.getMemberRef).toString
          if (Constants.moduleFieldNames.contains(fieldName)) {
            dataDeps.get(AbsPath.Local(propertyWrite.getValue)) match {
              case Some(fromValues) =>
                return fromValues.flatMap {
                  case AbsVal.HasField(fieldName) =>
                    Some(fieldName)
                  case _ => None
                }
              case _ =>
            }
          }
        }
      case _ =>
    }
    Set()
  }

  /** Get possible opNodes for a [[SSAInstruction]]
    *
    * FIXME: defUse and symTable should be an attribute of some processor class
    */
  def getPossibleOpNodes(dataFlow: IFDSDataFlow,
                         g: GW,
                         symTable: SymbolTable,
                         instruction: SSAInstruction,
                         dataDeps: Map[AbsPath, Set[AbsVal]]): Set[NodeId] = {
    instruction match {
      case invokeInstruction: JavaScriptInvoke =>
        // Case 1: Data-flow analysis has defined some intermediate API name already (as well as tag), use this directly
        dataFlow.getOpNodeNameAndAttrs(invokeInstruction) match {
          case Some((name, attrs)) =>
            return Set(getPostApiInvocationNode(g, invokeInstruction, name, attrs))
          case None =>
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

  def doEntrypointDataFlowAnalysis(cg: CallGraph): List[String] = {
    val icfg = ExplodedInterproceduralCFG.make(cg)
    val dataflow = new IFDSDataFlow(icfg)
    val results = dataflow.solve
    val superGraph = dataflow.problem.getSupergraph

    val fieldNames = mutable.Set[String]()
    for (block <- superGraph.asScala) {
      if (Constants.isApplicationNode(block.getNode)) {
        val symTable = block.getNode.getIR.getSymbolTable
        val instruction = block.getDelegate.getInstruction
        if (instruction != null) {
          val dataFlowDeps = dataflow.getFacts(results, block)
          fieldNames.addAll(getModuleFieldNames(symTable, instruction, dataFlowDeps))
        }
      }
    }
    fieldNames.toList
  }

  /** IFDS based data-flow analysis
    * @param g Semantic graph writer
    * @param cg Call graph
    */
  def addDataFlowGraph(g: GW, cg: CallGraph): Unit = {
    val icfg = ExplodedInterproceduralCFG.make(cg)
    val dataflow = new IFDSDataFlow(icfg)
    val results = dataflow.solve

    /*
     Super graph is also called exploded CFG.
     Super graph is a call-graph of CGNode, and each CGNode is a CFG of blocks.
     */
    val superGraph = dataflow.problem.getSupergraph

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

          for (opNode <- getPossibleOpNodes(dataflow, g, symTable, instruction, dataFlowDeps)) {
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
                    opNode,
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
                          opNode,
                          Map(JsEdgeAttr.TYPE -> EdgeType.DATAFLOW.toString))
                      }
                    case AbsVal.Constant(v) =>
                      g.addEdge(
                        g.createNode(v, Map(JsNodeAttr.TYPE -> NodeType.Constant.toString)),
                        opNode,
                        Map(JsEdgeAttr.TYPE -> EdgeType.DATAFLOW.toString))
                    case AbsVal.Instance(className, invoke) =>
                      val fromOpNode = getPostApiInvocationNode(
                        g,
                        invoke,
                        className,
                        Map(JsNodeAttr.TYPE -> NodeType.Constant.toString, JsNodeAttr.TAG -> Tag.Instance.toString))
                      if (!g.getEdges.contains(fromOpNode, opNode)) {
                        g.addEdge(fromOpNode, opNode, Map(JsEdgeAttr.TYPE -> EdgeType.DATAFLOW.toString))
                      }
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
