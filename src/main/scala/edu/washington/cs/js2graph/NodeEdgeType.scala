package edu.washington.cs.js2graph

object NodeType extends Enumeration {
    val METHOD,
    SENSITIVE_PARENT,
    SENSITIVE_METHOD,
    STMT,
    CONST_STRING,
    CONST_INT,
    CONSTANT = Value
}

object EdgeType extends Enumeration {
    val FROM_SENSITIVE_PARENT_TO_SENSITIVE_API, CALL, DATAFLOW, DOMINATE = Value
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