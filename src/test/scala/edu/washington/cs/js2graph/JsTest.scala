package edu.washington.cs.js2graph

import java.io.{File, Reader, Writer}
import java.nio.charset.StandardCharsets
import java.nio.file.Files

import com.semantic_graph.writer.GexfWriter
import org.junit.Test
import com.semantic_graph.NodeId
import io.github.izgzhen.msbase.IOUtil
import org.junit.Assert._

class JsTest {
  private val record = false

  def compareString(expectedFile: String, actual: String): Unit = {
    if (record) {
      IOUtil.write(actual, expectedFile)
      return
    }
    val expected: String = IOUtil.readLines(expectedFile).mkString("\n") + "\n";
    val msg = String.format("===== Expected: %s =====\n\n===== Actual =====\n%s\n", expectedFile, actual)
    assertEquals(msg, expected, actual)
  }

  def compareSetOfSortedStrings(expectedFile: String, actual: List[String]): Unit = {
    val actualSortedSet = actual.toSet.toList.sorted
    if (record) {
      IOUtil.writeLines(actualSortedSet, expectedFile)
      return
    }
    val expected: List[String] = IOUtil.readLines(expectedFile)
    val msg = String.format("===== Expected: %s =====\n\n===== Actual =====\n%s\n\n===== Diff =====\n%s\n\n",
      expectedFile, actualSortedSet.mkString("\n"), expected.toSet.diff(actualSortedSet.toSet).mkString("\n"))
    assertEquals(msg, expected, actualSortedSet)
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

  private def getNodeStr(g: GexfWriter[JsNodeAttr.Value, JsEdgeAttr.Value], node: NodeId): String = {
    var nodeStr = g.getNodeLabel(node)
    val nodeAttrs = g.getNodeAttrs(node)
    nodeAttrs.get(JsNodeAttr.TAG) match {
      case Some(tag) => nodeStr = "[" + tag + "]" + nodeStr
      case _ =>
    }
    nodeAttrs.get(JsNodeAttr.TYPE) match {
      case Some(typeName) => nodeStr = nodeStr + ":" + typeName
      case _ =>
    }
    nodeStr
  }

  private def getNodeStrings(g: GexfWriter[JsNodeAttr.Value, JsEdgeAttr.Value]): List[String] = {
    g.getNodes.map(getNodeStr(g, _)).toList.sorted
  }

  private def testJS(jsPath: String, isJsGenerated: Boolean = false): List[String] = {
    val g = new GexfWriter[JsNodeAttr.Value, JsEdgeAttr.Value]()
    val cg = JSFlowGraph.addCallGraph(jsPath)

    JSFlowGraph.addDataFlowGraph(g, cg)
    val jsPathFile = new File(jsPath)
    val jsDir = jsPathFile.getParentFile
    val jsName = jsPathFile.getName
    var jsGeneratedDir = jsDir.getAbsolutePath
    if (!isJsGenerated) {
      jsGeneratedDir = jsDir + "/generated"
    }

    // Record basic facts about WALA
    compareString(jsGeneratedDir + "/" + jsName.replace(".js", ".cg.txt"), cg.toString)
    val ir = Constants.getIRofCG(cg)
    compareString(jsGeneratedDir + "/" + jsName.replace(".js", ".ir.txt"), ir)

    val nodeStrs = getNodeStrings(g)
    compareSetOfSortedStrings(jsGeneratedDir + "/" + jsName.replace(".js", ".nodes.txt"), nodeStrs)
    compareSetOfSortedStrings(jsGeneratedDir + "/" + jsName.replace(".js", ".edges.txt"), g.getEdges.map { case (u, v) =>
      val lu = getNodeStr(g, u)
      val lv = getNodeStr(g, v)
      lu + " -[" + g.getEdgeAttrs(u, v)(JsEdgeAttr.TYPE) + "]-> " + lv
    }.toList)
    nodeStrs
  }

  private def testJSWithEntrypoints(jsPath: String): Unit = {
    val jsPathFile = new File(jsPath)
    val jsDir = jsPathFile.getParentFile
    val jsName = jsPathFile.getName
    val jsGeneratedDir = jsDir + "/generated"
    val entrypointsJsPath = jsGeneratedDir + "/" + jsName.replace(".js", ".entrypoints.js")
    val entrypoints = JSFlowGraph.getAllMethods(jsPath)
    compareSetOfSortedStrings(entrypointsJsPath, entrypoints)
    val newJsPath = jsGeneratedDir + "/" + jsName
    mergeFiles(
      new File(newJsPath),
      new File(jsPath),
      new File(entrypointsJsPath))
    testJS(newJsPath, isJsGenerated = true)
  }

  /**
   * Type: small e2e test
   * Source: a snippet that test inter-procedural data-flow
   */
  @Test def testExampleJS1(): Unit = {
    testJS("src/test/resources/small/example.js")
  }

  /**
   * Type: large e2e test
   * Source: a vulnerable snippet from event-stream package
   *
   * See https://github.com/semantic-graph/seguard-java/issues/2 for some related issue
   * FIXME: The current edges list is not perfect since the new object-access-path based node is not connected to other
   *        nodes. It should be able to find their replacements.
   *
   */
  @Test
  def testEventStreamJS(): Unit = {
    testJS("src/test/resources/large/eventstream.js")
  }

  /**
   * Type: large e2e test
   * Source: a vulnerable snippet from angular-location-update package
   *
   */
  @Test
  def testAngularLocationUpdateJS(): Unit = {
    testJS("src/test/resources/large/angular-location-update.js")
  }

  /**
   * Type: large e2e test
   * Source: a vulnerable snippet from conventional-changelog package
   *
   */
  @Test
  def testConventionalChangelogIndexJS(): Unit = {
    testJS("src/test/resources/large/conventional-changelog-index.js")
  }

  /**
   * Type: large e2e test
   * Source: a vulnerable snippet from eslint-config-airbnb-standard package
   *
   */
  @Test
  def testEslintConfigAirbnbStandard(): Unit = {
    testJS("src/test/resources/large/eslint-config-build.js")
  }

  /**
   * Type: small e2e test
   * Source: a snippet that uses binary arithmetic op for testing data-flow deps
   */
  @Test
  def testExampleJS2(): Unit = {
    testJS("src/test/resources/small/example2.js")
  }

  /**
   * Type: large e2e test
   * Source: a snippet that uses execSync
   */
  @Test
  def testExampleJS3(): Unit = {
    testJSWithEntrypoints("src/test/resources/large/example3.js")
  }

  /**
   * Type: small e2e test
   * Source: a snippet that uses callback
   */
  @Test
  def testExampleJS4(): Unit = {
    testJS("src/test/resources/small/example4.js")
  }

  /**
   * Type: small e2e test
   * Source: a snippet that uses eval
   */
  @Test
  def testExampleJS5(): Unit = {
    testJS("src/test/resources/small/example5.js")
  }

// FIXME: flaky
//  /**
//   * Type: Regression test
//   * Source: conventional-changelog package from NPM
//   */
//  @Test
//  def testConventionalExamples(): Unit = {
//    val dir = "src"/"test"/"resources"/"regression"/"conventional-changelog"
//    for (jsFile <- dir.glob("*.js")) {
//      testJSWithEntrypoints(jsFile.toString)
//    }
//  }
}
