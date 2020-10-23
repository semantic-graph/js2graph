package edu.washington.cs.js2graph

import edu.washington.cs.js2graph.JSFlowGraph.doEntrypointDataFlowAnalysis

import scala.collection.mutable

object EntrypointAnalysis {
  /**
    * Used for "entrypoint" mode to generate a dummy main for the analyzed module's exported API
    */
  def getAllModuleEntrypoints(jsPath: String): List[String] = {
    val cg = CallGraphAnalysis.getCallGraph(jsPath)
    val fieldNames = doEntrypointDataFlowAnalysis(cg)
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
