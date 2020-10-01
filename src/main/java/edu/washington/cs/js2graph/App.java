package edu.washington.cs.js2graph;

import com.semantic_graph.writer.GexfWriter;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.log4j.PropertyConfigurator;

import lombok.val;

public class App {
    /**
     * The entry points to all analyses
     */
    public static void main(String[] args) throws Exception {
        Options options = new Options();
        options.addOption(Option.builder().argName("mode").hasArg().longOpt("mode").desc("mode: core, entrypoints").build());
        options.addOption(Option.builder().argName("js").hasArg().longOpt("js").desc("JS path").build());
        options.addOption(Option.builder().argName("outputPath").hasArg().longOpt("outputPath").desc("output path").build());
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = parser.parse(options, args);
        PropertyConfigurator.configure("log4j.properties");
        String jsPath = cmd.getOptionValue("js");
        String mode = cmd.getOptionValue("mode");
        String outputPath = cmd.getOptionValue("outputPath");

        switch (mode) {
            case "core":
                    // Perform main JavaScript analysis
                    val g = new GexfWriter<JsNodeAttr$.Value, JsEdgeAttr$.Value>();
                    val cg = JSFlowGraph.addCallGraph(g, jsPath);
                    JSFlowGraph.addDataFlowGraph(g, cg);
                    g.write(outputPath);
                System.out.println("Written to " + outputPath);
                break;
            case "entrypoints":
                // Extract a list of entry-points for main analysis later
                // This is to avoid missing non-whole program analysis
                JSFlowGraph.writeEntrypoints(jsPath, outputPath);
                break;
            default:
                throw new RuntimeException("Unsupported mode: " + mode);
        }
    }
}