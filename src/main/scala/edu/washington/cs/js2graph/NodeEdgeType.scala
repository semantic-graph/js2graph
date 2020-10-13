package edu.washington.cs.js2graph

object NodeType extends Enumeration {
  val CONSTANT, SINGLETON, INSTANCE = Value
}

object EdgeType extends Enumeration {
  val DATAFLOW = Value
}

object Tag extends Enumeration {
  val
  /** Call of constructor API that returns *some* instance
    */
  Construct,
  /** Call of API on *some* instance
    */
  Call,
  /** Field reference of some instance
    */
  FieldRef = Value
}

object JsNodeAttr extends Enumeration with Serializable {
  type JsNodeAttr = Value
  val DOMAIN = Value("domain")
  val TYPE = Value("type")
  val TAG = Value("tag")
}

object JsEdgeAttr extends Enumeration with Serializable {
  type JsEdgeAttr = Value
  val TYPE = Value("type")
  val TAG = Value("tag")
}
