package edu.washington.cs.js2graph

import java.io.{BufferedWriter, File, FileWriter}
import java.nio.file.Paths

import com.ibm.wala.cast.js.ipa.callgraph.JSCallGraphUtil
import com.ibm.wala.cast.js.ssa._
import com.ibm.wala.cast.js.translator.PatchedCAstRhinoTranslatorFactory
import com.ibm.wala.cast.js.types.JavaScriptMethods
import com.ibm.wala.examples.analysis.js.JSCallGraphBuilderUtil
import com.ibm.wala.ipa.callgraph.{CGNode, CallGraph}
import com.ibm.wala.ipa.cfg.ExplodedInterproceduralCFG
import com.ibm.wala.ssa._
import com.semantic_graph.NodeId
import com.semantic_graph.writer.GraphWriter
import edu.washington.cs.js2graph.JsEdgeAttr.JsEdgeAttr

import scala.collection.mutable
import scala.jdk.CollectionConverters._

object JSFlowGraph {
  private val postApiInvocationNodes = mutable.Map[JavaScriptInvoke, NodeId]()
  def getPostApiInvocationNode(g: GraphWriter[JsNodeAttr.Value, JsEdgeAttr.Value],
                               invoke: JavaScriptInvoke,
                               apiName: String,
                               tag: Tag.Value): NodeId = {
    if (!postApiInvocationNodes.contains(invoke)) {
      postApiInvocationNodes.addOne(
        invoke,
        g.createNode(apiName, Map(JsNodeAttr.TYPE -> NodeType.INSTANCE.toString, JsNodeAttr.TAG -> tag.toString)))
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

  // This can't process name like $$jscomp$generator$Engine_$$throw_$ meaningfully
  private def toFunctionCall(name: String): String = {
    val (dollars, rest) = splitAtLast(name.stripPrefix("$").stripSuffix("$"), '$')
    val ret = dollars + rest.replace("$$", ".prototype.").replace("$", ".") + "();"
    assert(!ret.contains(".."))
    ret
  }

  def getAllMethods(jsPath: String): List[String] = {
    val cg = addCallGraph(jsPath)
    val cha = cg.getClassHierarchy
    val methods = cha.asScala.filter(!_.getName.toString.contains("prologue.js")).flatMap(_.getAllMethods.asScala).toList
    val lines = methods.map(_.getDeclaringClass.getName.toString.split("/")).filter(_.length > 1).map(xs => toFunctionCall(xs(1)))
    var ret = List[String]()
    for (line <- lines) {
      if (!line.contains("@")) {
        ret :+= line
      }
    }
    ret
  }

  def writeEntrypoints(jsPath: String, outputPath: String): Unit = {
    val file = new File(outputPath)
    val bw = new BufferedWriter(new FileWriter(file))
    for (line <- getAllMethods(jsPath)) {
      bw.write(line + "\n")
    }
    bw.close()
  }

  /** Add the callgraph to semantic graph
    * @param jsPath Path to the analyzed JS file
    * @return The constructed call-graph (for later use in other analysis)
    */
  def addCallGraph(jsPath: String): CallGraph = {
    val path = Paths.get(jsPath)
    JSCallGraphUtil.setTranslatorFactory(new PatchedCAstRhinoTranslatorFactory)
    val cg = JSCallGraphBuilderUtil.makeScriptCG(path.getParent.toString, path.getFileName.toString)
    if (Constants.debug.nonEmpty) {
      cg.stream()
        .filter(Constants.isApplicationNode)
        .forEach(node => {
          println("------------------------------------------------")
          println(node.getIR)
        })
    }
    cg
  }

  /** Get abstract node for a [[SSAInstruction]]
    *
    * FIXME: defUse and symTable should be an attribute of some processor class
    */
  def getSinkSNodes(dataFlow: IFDSDataFlow,
                    g: GraphWriter[JsNodeAttr.Value, JsEdgeAttr.Value],
                    symTable: SymbolTable,
                    instruction: SSAInstruction,
                    dataDeps: Map[AbsPath, Set[AbsVal]]): Set[NodeId] = {
    instruction match {
      case invokeInstruction: JavaScriptInvoke =>
        // Case 1: Call an API
        dataFlow.getApiNameAndTag(invokeInstruction) match {
          case Some((name, tag)) =>
            return Set(getPostApiInvocationNode(g, invokeInstruction, name, tag))
          case None =>
        }
        // Case 2: Call an API (not constructor)
        if (invokeInstruction.getNumberOfUses >= 2) {
          val dispatchFuncIndex = invokeInstruction.getUse(0)
          val dispatchBaseIndex = invokeInstruction.getUse(1)
          if (symTable.isConstant(dispatchFuncIndex)) {
            val dispatchFunc = symTable.getConstantValue(dispatchFuncIndex).toString
            dataDeps.get(AbsPath.Local(dispatchBaseIndex)) match {
              case None =>
              case Some(fromValues) =>
                return fromValues.flatMap {
                  case AbsVal.Global(name) =>
                    if (Constants.isLibraryGlobalName(name)) {
                      Some(
                        g.createNode(
                          name + "." + dispatchFunc,
                          Map(JsNodeAttr.TYPE -> NodeType.GLOBAL.toString, JsNodeAttr.TAG -> Tag.Call.toString)))
                    } else {
                      None
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
                      Map(JsNodeAttr.TYPE -> NodeType.INSTANCE.toString, JsNodeAttr.TAG -> Tag.FieldRef.toString)))
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

  /** IFDS based data-flow analysis
    * @param g Semantic graph writer
    * @param cg Call graph
    */
  def addDataFlowGraph(g: GraphWriter[JsNodeAttr.Value, JsEdgeAttr.Value], cg: CallGraph): Unit = {
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

    for (block <- superGraph.asScala) {
      if (Constants.isApplicationNode(block.getNode)) {
        val symTable = block.getNode.getIR.getSymbolTable
        // Each block corresponds to a _single_ instruction
        val instruction = block.getDelegate.getInstruction
        if (instruction != null) {
          val dataFlowDeps = dataflow.getFacts(results, block)

          for (toSNode <- getSinkSNodes(dataflow, g, symTable, instruction, dataFlowDeps)) {
            // Build semantic graph of SNode and SEdge
            val firstUseIdx = instruction match {
              case _: JavaScriptInvoke        => 2
              case _: JavaScriptPropertyWrite => 2
              case _                          => 0
            }
            for (iu <- firstUseIdx until instruction.getNumberOfUses) {
              val use = instruction.getUse(iu)
              // If the use is a constant
              if (symTable.isConstant(use) && symTable.getConstantValue(use) != null) {
                val v = symTable.getConstantValue(use).toString
                g.addEdge(
                  g.createNode(v, Map(JsNodeAttr.TYPE -> NodeType.CONSTANT.toString)),
                  toSNode,
                  Map(JsEdgeAttr.TYPE -> EdgeType.DATAFLOW.toString))
              } else {
                for (absVal <- resolveDataDeps(dataFlowDeps, AbsPath.Local(use))) {
                  absVal match {
                    case AbsVal.Global(name) =>
                      if (Constants.isLibraryGlobalName(name)) {
                        g.addEdge(
                          g.createNode(name, Map(JsNodeAttr.TYPE -> NodeType.GLOBAL.toString)),
                          toSNode,
                          Map(JsEdgeAttr.TYPE -> EdgeType.DATAFLOW.toString))
                      }
                    case AbsVal.Constant(v) =>
                      g.addEdge(
                        g.createNode(v, Map(JsNodeAttr.TYPE -> NodeType.CONSTANT.toString)),
                        toSNode,
                        Map(JsEdgeAttr.TYPE -> EdgeType.DATAFLOW.toString))
                    case AbsVal.Instance(name, invoke) =>
                      val fromSNode = getPostApiInvocationNode(g, invoke, name, Tag.Construct)
                      if (!g.getEdges.contains(fromSNode, toSNode)) {
                        g.addEdge(fromSNode, toSNode, Map(JsEdgeAttr.TYPE -> EdgeType.DATAFLOW.toString))
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
