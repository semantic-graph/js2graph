package edu.washington.cs.js2graph

object Constants {
  val nodeJsBuiltInGlobalNames = Set("process", "console", "document", "Buffer")

  def isLibraryGlobalName(name: String): Boolean = {
    if (name.startsWith("global ")) {
      val s = name.stripPrefix("global ")
      // FIXME: prefix is not a good heuristic
      nodeJsBuiltInGlobalNames.exists(name => s.startsWith(name))
    } else if (name.startsWith("require(")) {
      true
    } else {
      false
    }
  }

  /** Point to global context https://sourceforge.net/p/wala/mailman/message/32491808/
    */
  val WALAGlobalContext = "__WALA__int3rnal__global"

  val constructorAPINames = Set("createElement", "createDecipher")

  /** Whether a function is a API that is essentially an instance constructor
    *
    * If so, we will construct a API instance node during DFA for its return value.
    *
    * FIXME: Consider other APIs as well as the base namespace
    */
  def isConstructorAPI(funcName: String): Boolean = constructorAPINames.contains(funcName)

  val debug: Option[String] = sys.env.get("DEBUG")
}
