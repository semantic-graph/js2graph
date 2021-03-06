package edu.washington.cs.js2graph

import java.io.{File, Reader, Writer}
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import better.files._ // Provides syntactic sugar etc.

import com.semantic_graph.writer.GexfWriter
import org.junit.Test
import com.semantic_graph.NodeId
import edu.washington.cs.js2graph.Constants.GraphWriter
import io.github.izgzhen.msbase.IOUtil
import org.junit.Assert._

class JsTest {
  private val record = sys.env.contains("RECORD")

  private def recordString(expectedFile: String, actual: String): Unit = {
    if (record) {
      IOUtil.write(actual, expectedFile)
      return
    }
  }

  private def compareSortedStrings(expectedFile: String, actual: List[String]): Unit = {
    val actualSorted = actual.sorted
    if (record) {
      IOUtil.writeLines(actualSorted, expectedFile)
      return
    }
    val expected: List[String] = IOUtil.readLines(expectedFile)
    val msg = String.format(
      "===== Expected: %s =====\n\n===== Actual =====\n%s\n\n===== Diff =====\n%s\n\n",
      expectedFile,
      actualSorted.mkString("\n"),
      expected.toSet.diff(actualSorted.toSet).mkString("\n")
    )
    assertEquals(msg, expected, actualSorted)
  }

  private def transfer(source: Reader, destination: Writer): Unit = {
    val buffer = new Array[Char](1024 * 16)
    var len = source.read(buffer)
    while (len >= 0) {
      destination.write(buffer, 0, len)
      len = source.read(buffer)
    }
  }

  private def mergeFiles(output: File, inputfile1: File, inputfile2: File): Unit = {
    try {
      val sourceA = Files.newBufferedReader(inputfile1.toPath)
      val sourceB = Files.newBufferedReader(inputfile2.toPath)
      val destination = Files.newBufferedWriter(output.toPath, StandardCharsets.UTF_8)
      try {
        transfer(sourceA, destination)
        transfer(sourceB, destination)
      } finally {
        if (sourceA != null) sourceA.close()
        if (sourceB != null) sourceB.close()
        if (destination != null) destination.close()
      }
    }
  }

  private def getNodeStr(g: GraphWriter, node: NodeId): String = {
    var nodeStr = g.getNodeLabel(node)
    val nodeAttrs = g.getNodeAttrs(node)
    // Tag is hidden in this representation
    nodeAttrs.get(JsNodeAttr.TYPE) match {
      case Some(typeName) => nodeStr = nodeStr + ":" + typeName
      case _              =>
    }
    nodeStr
  }

  private def getNodeStrings(g: GraphWriter): List[String] = {
    g.getNodes.toList.map(getNodeStr(g, _))
  }

  private def getEdgeStrings(g: GraphWriter): List[String] = {
    g.getEdges.toList.map { case (u, v) =>
      val lu = getNodeStr(g, u)
      val lv = getNodeStr(g, v)
      lu + " -[" + g.getEdgeAttrs(u, v)(JsEdgeAttr.TYPE) + "]-> " + lv
    }
  }

  private def testJS(jsPath: String, isJsGenerated: Boolean = false): Unit = {
    val g = new GexfWriter[JsNodeAttr.Value, JsEdgeAttr.Value]()
    val cg = CallGraphAnalysis.getCallGraph(jsPath)
    val jsFlowGraph = new JSFlowGraph(g, cg)
    jsFlowGraph.addDataFlowGraph()
    val jsPathFile = new File(jsPath)
    val jsDir = jsPathFile.getParentFile
    val jsName = jsPathFile.getName
    var jsGeneratedDir = jsDir.getAbsolutePath
    if (!isJsGenerated) {
      jsGeneratedDir = jsDir + "/generated"
    }

    // Record basic facts about WALA
    recordString(jsGeneratedDir + "/" + jsName.replace(".js", ".cg.txt"), cg.toString)
    val ir = Constants.getIRofCG(cg)
    recordString(jsGeneratedDir + "/" + jsName.replace(".js", ".ir.txt"), ir)

    val nodeStrs = getNodeStrings(g)
    val edgeStrs = getEdgeStrings(g)
    compareSortedStrings(jsGeneratedDir + "/" + jsName.replace(".js", ".nodes.txt"), nodeStrs)
    compareSortedStrings(jsGeneratedDir + "/" + jsName.replace(".js", ".edges.txt"), edgeStrs)
  }

  private def testJSWithEntrypoints(jsPath: String): Unit = {
    val jsPathFile = new File(jsPath)
    val jsDir = jsPathFile.getParentFile
    val jsName = jsPathFile.getName
    val jsGeneratedDir = jsDir + "/generated"
    val entrypointsJsPath = jsGeneratedDir + "/" + jsName.replace(".js", ".entrypoints.js")
    val entrypointsAnalysis = new EntrypointAnalysis(CallGraphAnalysis.getCallGraph(jsPath))
    compareSortedStrings(entrypointsJsPath, entrypointsAnalysis.getAllModuleEntrypoints)
    val newJsPath = jsGeneratedDir + "/" + jsName
    mergeFiles(new File(newJsPath), new File(jsPath), new File(entrypointsJsPath))
    testJS(newJsPath, isJsGenerated = true)
  }

  /** Type: small e2e test
    * Source: a snippet that test inter-procedural data-flow
    */
  @Test def testExampleJS1(): Unit = {
    testJS("src/test/resources/small/example.js")
  }

  /** Type: large e2e test
    * Source: a vulnerable snippet from event-stream package
    *
    * See https://github.com/semantic-graph/seguard-java/issues/2 for some related issue
    * FIXME: The current edges list is not perfect since the new object-access-path based node is not connected to other
    *        nodes. It should be able to find their replacements.
    */
  @Test
  def testEventStreamJS(): Unit = {
    testJS("src/test/resources/large/eventstream.js")
  }

  /** Type: large e2e test
    * Source: a vulnerable snippet from angular-location-update package
    */
  @Test
  def testAngularLocationUpdateJS(): Unit = {
    testJS("src/test/resources/large/angular-location-update.js")
  }

  /** Type: large e2e test
    * Source: a vulnerable snippet from conventional-changelog package
    */
  @Test
  def testConventionalChangelogIndexJS(): Unit = {
    testJSWithEntrypoints("src/test/resources/large/conventional-changelog-index.js")
  }

  /** Type: large e2e test
    * Source: a vulnerable snippet from eslint-config-airbnb-standard package
    */
  @Test
  def testEslintConfigAirbnbStandard(): Unit = {
    testJS("src/test/resources/large/eslint-config-build.js")
  }

  /** Type: small e2e test
    * Source: a snippet that uses binary arithmetic op for testing data-flow deps
    */
  @Test
  def testExampleJS2(): Unit = {
    testJS("src/test/resources/small/example2.js")
  }

  /** Type: large e2e test
    * Source: a snippet that uses execSync
    */
  @Test
  def testExampleJS3(): Unit = {
    testJSWithEntrypoints("src/test/resources/large/example3.js")
  }

  /** Type: small e2e test
    * Source: a snippet that uses callback
    */
  @Test
  def testExampleJS4(): Unit = {
    testJS("src/test/resources/small/example4.js")
  }

  /** Type: small e2e test
    * Source: a snippet that uses eval
    */
  @Test
  def testExampleJS5(): Unit = {
    testJS("src/test/resources/small/example5.js")
  }

  /** Type: small e2e test
    * Source: a snippet that imports user modules
    */
  @Test
  def testExampleJS6(): Unit = {
    testJS("src/test/resources/small/example6.js")
  }

  /** Type: small e2e test
    * Source: a snippet for inter-proc data-flow
    */
  @Test
  def testExampleJS7(): Unit = {
    testJS("src/test/resources/small/example7.js")
  }

  /** Type: small e2e test
    * Source: a snippet that exposes an API using node JS module system
    */
  @Test
  def testExampleJS8(): Unit = {
    testJSWithEntrypoints("src/test/resources/small/example8.js")
  }

  /** Type: Regression test
    * Source: conventional-changelog package from NPM
    *
    * FIXME: Flaky tests
    */
  @Test
  def testConventionalExamples(): Unit = {
    val dir = "src" / "test" / "resources" / "regression" / "conventional-changelog"
    for (jsFile <- dir.glob("*.js")) {
      // testJSWithEntrypoints(jsFile.toString)
    }
  }
}
