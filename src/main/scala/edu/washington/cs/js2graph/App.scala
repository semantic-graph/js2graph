package edu.washington.cs.js2graph

import com.semantic_graph.writer.GexfWriter
import org.apache.commons.cli.{DefaultParser, Option, Options}
import org.apache.log4j.PropertyConfigurator

object App {
    /**
     * The entry points to all analyses
     */
    def main(args: Array[String]): Unit = {
        val options = new Options()
        options.addOption(Option.builder().argName("mode").hasArg().longOpt("mode").desc("mode: core, entrypoints").build())
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
                // Perform main JavaScript analysis
                val g = new GexfWriter[JsNodeAttr.Value, JsEdgeAttr.Value]()
                val cg = JSFlowGraph.addCallGraph(jsPath)
                JSFlowGraph.addDataFlowGraph(g, cg)
                g.write(outputPath)
                println("Written to " + outputPath)
            case "entrypoints" =>
                // Extract a list of entry-points for main analysis later
                // This is to avoid missing non-whole program analysis
                JSFlowGraph.writeEntrypoints(jsPath, outputPath)
            case _ =>
                throw new RuntimeException("Unsupported mode: " + mode)
        }
    }
}