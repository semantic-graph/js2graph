package edu.washington.cs.js2graph

object NodeType extends Enumeration {
  val
  /** Constant value
    */
  Constant,
  /** Primitive Operation
    */
  PrimOp,
  /** Call of constructor API that returns some instance
    */
  Construct,
  /** Call of API on some instance
    */
  Call,
  /** Field reference on some instance
    */
  FieldRef = Value
}

object EdgeType extends Enumeration {
  val DATAFLOW = Value
}

object Tag extends Enumeration {
  val
  /** Invocation base is a singleton object
    */
  Singleton,
  /** Invocation base is an instance object
    */
  Instance = Value
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
