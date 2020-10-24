package edu.washington.cs.js2graph

import com.ibm.wala.cast.js.ssa.JavaScriptPropertyWrite
import com.ibm.wala.ipa.callgraph.CallGraph
import com.ibm.wala.ipa.cfg.ExplodedInterproceduralCFG
import com.ibm.wala.ssa.{SSAInstruction, SymbolTable}

import scala.collection.mutable
import scala.jdk.CollectionConverters._

object EntrypointAnalysis {

  /** See UnitTest.testGetModuleFieldNames for its explanation
    */
  def getModuleFieldNames(symTable: SymbolTable, instruction: SSAInstruction, dataDeps: Map[AbsPath, Set[AbsVal]]): Set[String] = {
    instruction match {
      case propertyWrite: JavaScriptPropertyWrite =>
        if (symTable.isConstant(propertyWrite.getMemberRef)) {
          val fieldName = symTable.getConstantValue(propertyWrite.getMemberRef).toString
          if (Constants.moduleFieldNames.contains(fieldName)) {
            dataDeps.get(AbsPath.Local(propertyWrite.getValue)) match {
              case Some(fromValues) =>
                return fromValues.flatMap {
                  case AbsVal.HasField(subFieldName) => Some(subFieldName)
                  case _                             => None
                }
              case _ =>
            }
          }
        }
      case _ =>
    }
    Set()
  }
}

class EntrypointAnalysis(cg: CallGraph) {
  private def doEntrypointDataFlowAnalysis(): List[String] = {
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
          fieldNames.addAll(EntrypointAnalysis.getModuleFieldNames(symTable, instruction, dataFlowDeps))
        }
      }
    }
    fieldNames.toList
  }

  /** Used for "entrypoint" mode to generate a dummy main for the analyzed module's exported API
    */
  def getAllModuleEntrypoints: List[String] = {
    val fieldNames = doEntrypointDataFlowAnalysis()
    val ret = mutable.Queue[String]()
    for (fieldName <- fieldNames) {
      ret.addOne("module.exports." + fieldName + "();")
    }
    // FIXME(Zhen): Sometimes a single function is exported, e.g. module.exports = function() { ... }
    //    In this case, we need to do a finer-grained type analysis to know what is exported or just *try* to call it
    //    as of now.
    ret.addOne("module.exports();")
    ret.toList
  }
}
