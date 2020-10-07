package edu.washington.cs.js2graph

object NodeType extends Enumeration {
    val METHOD, STMT, CONSTANT,

    /**
     * With Tag = Call: Call of API on *some* global thing (similar to static function)
     */
    GLOBAL,

    /**
     * With Tag = Call: Call of API on *some* instance
     * With Tag = Construct: Call of constructor API that returns *some* instance
     */
    INSTANCE = Value
}

object EdgeType extends Enumeration {
    val DATAFLOW, DOMINATE = Value
}

object Tag extends Enumeration {
    val Construct, Call, FieldRef = Value
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