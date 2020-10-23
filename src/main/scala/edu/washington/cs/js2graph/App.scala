package edu.washington.cs.js2graph

import java.io.{BufferedWriter, File, FileWriter}

import com.semantic_graph.writer.GexfWriter
import org.apache.commons.cli.{DefaultParser, Option, Options}
import org.apache.log4j.PropertyConfigurator

object App {

  /** Extract a list of entry-points for a dummy main based whole-program analysis
    */
  def writeEntrypoints(jsPath: String, outputPath: String): Unit = {
    val file = new File(outputPath)
    val bw = new BufferedWriter(new FileWriter(file))
    val cg = CallGraphAnalysis.getCallGraph(jsPath)
    val entrypointAnalysis = new EntrypointAnalysis(cg)
    for (line <- entrypointAnalysis.getAllModuleEntrypoints) {
      bw.write(line + "\n")
    }
    bw.close()
  }

  /** The entry points to all analyses
    */
  def main(args: Array[String]): Unit = {
    val options = new Options()
    options.addOption(
      Option
        .builder()
        .argName("mode")
        .hasArg()
        .longOpt("mode")
        .desc("""mode:
        |- core: Perform core JavaScript analysis
        |- entrypoints: Generate a JS file of entrypoint invocations""".stripMargin)
        .build())
    options.addOption(Option.builder().argName("js").hasArg().longOpt("js").desc("JS path").build())
    options.addOption(Option.builder().argName("outputPath").hasArg().longOpt("outputPath").desc("output path").build())
    val parser = new DefaultParser()
    val cmd = parser.parse(options, args)
    PropertyConfigurator.configure("log4j.properties")
    val jsPath = cmd.getOptionValue("js")
    val mode = cmd.getOptionValue("mode")
    val outputPath = cmd.getOptionValue("outputPath")

    mode match {
      case "core" =>
        val g = new Constants.GraphWriter()
        val cg = CallGraphAnalysis.getCallGraph(jsPath)
        val jsFlowGraph = new JSFlowGraph(g, cg)
        jsFlowGraph.addDataFlowGraph()
        g.write(outputPath)
        println("Written to " + outputPath)
      case "entrypoints" => writeEntrypoints(jsPath, outputPath)
      case _ =>
        throw new RuntimeException("Unsupported mode: " + mode)
    }
  }
}
