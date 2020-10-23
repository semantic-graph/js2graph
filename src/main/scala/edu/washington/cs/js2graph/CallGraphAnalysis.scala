package edu.washington.cs.js2graph

import java.nio.file.Paths

import com.ibm.wala.cast.js.ipa.callgraph.JSCallGraphUtil
import com.ibm.wala.cast.js.nodejs.PatchedNodejsCallGraphBuilderUtil
import com.ibm.wala.cast.js.translator.PatchedCAstRhinoTranslatorFactory
import com.ibm.wala.ipa.callgraph.CallGraph

object CallGraphAnalysis {

  /** Get the call-graph for later use in other analysis
    * @param jsPath Path to the analyzed JS file
    * @return The constructed call-graph
    */
  def getCallGraph(jsPath: String): CallGraph = {
    val path = Paths.get(jsPath)
    // Translate from Rhino AST to WALA AST
    JSCallGraphUtil.setTranslatorFactory(new PatchedCAstRhinoTranslatorFactory)
    val builder = PatchedNodejsCallGraphBuilderUtil.makeCGBuilder(path.toFile)
    // TODO: explain the used call-graph algorithm
    val cg = builder.makeCallGraph(builder.getOptions)
    if (Constants.debug.nonEmpty) {
      println(Constants.getIRofCG(cg))
    }
    cg
  }

}
