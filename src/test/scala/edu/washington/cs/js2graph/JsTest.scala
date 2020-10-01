package edu.washington.cs.js2graph

import java.io.{File, Reader, Writer}
import java.nio.charset.StandardCharsets
import java.nio.file.Files

import com.semantic_graph.writer.GexfWriter
import org.junit.Test

import better.files._

import io.github.izgzhen.msbase.IOUtil

import org.junit.Assert._


class JsTest {
  private val record = false

  def compareSetOfStrings(expectedFile: String, actual: List[String]): Unit = {
    if (record) {
      IOUtil.writeLines(actual, expectedFile)
      return
    }
    val expected: List[String] = IOUtil.readLines(expectedFile)
    val msg = String.format("\nActual: %s\n\nDiff: %s\n\n", actual.mkString("\n"), expected.toSet.diff(actual.toSet).mkString("\n"))
    assertEquals(msg, expected, actual)
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

  private def testExampleJS(jsPath: String): Set[String] = {
    val g = new GexfWriter[JsNodeAttr.Value, JsEdgeAttr.Value]()
    val cg = JSFlowGraph.addCallGraph(g, jsPath)
    JSFlowGraph.addDataFlowGraph(g, cg)
    val labels = g.getNodes.map(i => g.getNodeLabel(i))
    compareSetOfStrings(jsPath.replace(".js", ".nodes.txt"), labels.toList)
    compareSetOfStrings(jsPath.replace(".js", ".edges.txt"), g.getEdges.map { case (u, v) =>
      val lu = g.getNodeLabel(u)
      val lv = g.getNodeLabel(v)
      lu + "-[" + g.getEdgeAttrs(u, v)(JsEdgeAttr.TYPE) + "]->" + lv
    }.toList)
    g.write(jsPath.replace(".js", ".gexf"))
    labels
  }

  private def testJSWithEntrypoints(jsPath: String): Unit = {
    val jsPathFile = new File(jsPath)
    val jsDir = jsPathFile.getParentFile
    val jsName = jsPathFile.getName
    val jsGeneratedDir = jsDir.getParent + "/generated"
    val entrypointsJsPath = jsGeneratedDir + "/" + jsName.replace(".js", ".entrypoints.js")
    val entrypoints = JSFlowGraph.getAllMethods(jsPath)
    compareSetOfStrings(entrypointsJsPath, entrypoints)
    val newJsPath = jsGeneratedDir + "/" + jsName
    mergeFiles(
      new File(newJsPath),
      new File(jsPath),
      new File(entrypointsJsPath))
    testExampleJS(newJsPath)
  }

  /**
   * Type: small e2e test
   * Source: a snippet that test inter-procedural data-flow
   */
  @Test def testExampleJS1(): Unit = {
    testExampleJS("src/test/resources/js/example.js")
  }

  /**
   * Type: e2e test
   * Source: a vulnerable snippet from event-stream package
   *
   * See https://github.com/semantic-graph/seguard-java/issues/2 for some related issue
   * FIXME: The current edges list is not perfect since the new object-access-path based node is not connected to other
   *        nodes. It should be able to find their replacements.
   *
   */
  @Test
  def testEventStreamJS(): Unit = {
    val labels = testExampleJS("src/test/resources/eventstream.js")
    assertTrue(labels.contains("process[env][npm_package_description]"))
  }

  /**
   * Type: small e2e test
   * Source: a snippet that uses binary arithmetic op for testing data-flow deps
   */
  @Test
  def testExampleJS2(): Unit = {
    testExampleJS("src/test/resources/js/example2.js")
  }

  /**
   * Type: e2e test
   * Source: a snippet that uses execSync
   */
  @Test
  def testExampleJS3(): Unit = {
    testJSWithEntrypoints("src/test/resources/js/example3.js")
  }

  /**
   * Type: Regression test
   * Source: conventional-changelog package from NPM
   */
  @Test
  def testConventionalExamples(): Unit = {
    val dir = "src"/"test"/"resources"/"conventional-changelog"/"js"
    for (jsFile <- dir.glob("*.js")) {
      testJSWithEntrypoints(jsFile.toString)
    }
  }
}
