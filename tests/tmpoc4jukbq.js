var $jscomp = $jscomp || {};
$jscomp.scope = {};
$jscomp.createTemplateTagFirstArg = function $$jscomp$createTemplateTagFirstArg$($arrayStrings$$) {
  return $arrayStrings$$.raw = $arrayStrings$$;
};
$jscomp.createTemplateTagFirstArgWithRaw = function $$jscomp$createTemplateTagFirstArgWithRaw$($arrayStrings$$, $rawArrayStrings$$) {
  $arrayStrings$$.raw = $rawArrayStrings$$;
  return $arrayStrings$$;
};
$jscomp.arrayIteratorImpl = function $$jscomp$arrayIteratorImpl$($array$$) {
  var $index$$ = 0;
  return function() {
    return $index$$ < $array$$.length ? {done:!1, value:$array$$[$index$$++]} : {done:!0};
  };
};
$jscomp.arrayIterator = function $$jscomp$arrayIterator$($array$$) {
  return {next:$jscomp.arrayIteratorImpl($array$$)};
};
$jscomp.makeIterator = function $$jscomp$makeIterator$($iterable$$) {
  var $iteratorFunction$$ = "undefined" != typeof Symbol && Symbol.iterator && $iterable$$[Symbol.iterator];
  return $iteratorFunction$$ ? $iteratorFunction$$.call($iterable$$) : $jscomp.arrayIterator($iterable$$);
};
$jscomp.arrayFromIterator = function $$jscomp$arrayFromIterator$($iterator$$) {
  for (var $i$$, $arr$$ = []; !($i$$ = $iterator$$.next()).done;) {
    $arr$$.push($i$$.value);
  }
  return $arr$$;
};
$jscomp.arrayFromIterable = function $$jscomp$arrayFromIterable$($iterable$$) {
  return $iterable$$ instanceof Array ? $iterable$$ : $jscomp.arrayFromIterator($jscomp.makeIterator($iterable$$));
};
$jscomp.ASSUME_ES5 = !1;
$jscomp.ASSUME_NO_NATIVE_MAP = !1;
$jscomp.ASSUME_NO_NATIVE_SET = !1;
$jscomp.SIMPLE_FROUND_POLYFILL = !1;
$jscomp.ISOLATE_POLYFILLS = !1;
$jscomp.objectCreate = $jscomp.ASSUME_ES5 || "function" == typeof Object.create ? Object.create : function($prototype$$) {
  var $ctor$$ = function $$ctor$$$() {
  };
  $ctor$$.prototype = $prototype$$;
  return new $ctor$$;
};
$jscomp.underscoreProtoCanBeSet = function $$jscomp$underscoreProtoCanBeSet$() {
  var $x$$ = {a:!0}, $y$$ = {};
  try {
    return $y$$.__proto__ = $x$$, $y$$.a;
  } catch ($e$$) {
  }
  return !1;
};
$jscomp.setPrototypeOf = "function" == typeof Object.setPrototypeOf ? Object.setPrototypeOf : $jscomp.underscoreProtoCanBeSet() ? function($target$$, $proto$$) {
  $target$$.__proto__ = $proto$$;
  if ($target$$.__proto__ !== $proto$$) {
    throw new TypeError($target$$ + " is not extensible");
  }
  return $target$$;
} : null;
$jscomp.inherits = function $$jscomp$inherits$($childCtor$$, $parentCtor$$) {
  $childCtor$$.prototype = $jscomp.objectCreate($parentCtor$$.prototype);
  $childCtor$$.prototype.constructor = $childCtor$$;
  if ($jscomp.setPrototypeOf) {
    var $p_setPrototypeOf$$ = $jscomp.setPrototypeOf;
    $p_setPrototypeOf$$($childCtor$$, $parentCtor$$);
  } else {
    for ($p_setPrototypeOf$$ in $parentCtor$$) {
      if ("prototype" != $p_setPrototypeOf$$) {
        if (Object.defineProperties) {
          var $descriptor$$ = Object.getOwnPropertyDescriptor($parentCtor$$, $p_setPrototypeOf$$);
          $descriptor$$ && Object.defineProperty($childCtor$$, $p_setPrototypeOf$$, $descriptor$$);
        } else {
          $childCtor$$[$p_setPrototypeOf$$] = $parentCtor$$[$p_setPrototypeOf$$];
        }
      }
    }
  }
  $childCtor$$.superClass_ = $parentCtor$$.prototype;
};
$jscomp.owns = function $$jscomp$owns$($obj$$, $prop$$) {
  return Object.prototype.hasOwnProperty.call($obj$$, $prop$$);
};
$jscomp.assign = "function" == typeof Object.assign ? Object.assign : function($target$$, $var_args$$) {
  for (var $i$$ = 1; $i$$ < arguments.length; $i$$++) {
    var $source$$ = arguments[$i$$];
    if ($source$$) {
      for (var $key$$ in $source$$) {
        $jscomp.owns($source$$, $key$$) && ($target$$[$key$$] = $source$$[$key$$]);
      }
    }
  }
  return $target$$;
};
$jscomp.defineProperty = $jscomp.ASSUME_ES5 || "function" == typeof Object.defineProperties ? Object.defineProperty : function($target$$, $property$$, $descriptor$$) {
  if ($target$$ == Array.prototype || $target$$ == Object.prototype) {
    return $target$$;
  }
  $target$$[$property$$] = $descriptor$$.value;
  return $target$$;
};
$jscomp.getGlobal = function $$jscomp$getGlobal$($passedInThis_possibleGlobals$$) {
  $passedInThis_possibleGlobals$$ = ["object" == typeof globalThis && globalThis, $passedInThis_possibleGlobals$$, "object" == typeof window && window, "object" == typeof self && self, "object" == typeof global && global];
  for (var $i$$ = 0; $i$$ < $passedInThis_possibleGlobals$$.length; ++$i$$) {
    var $maybeGlobal$$ = $passedInThis_possibleGlobals$$[$i$$];
    if ($maybeGlobal$$ && $maybeGlobal$$.Math == Math) {
      return $maybeGlobal$$;
    }
  }
  throw Error("Cannot find global object");
};
$jscomp.global = $jscomp.getGlobal(this);
$jscomp.polyfills = {};
$jscomp.propertyToPolyfillSymbol = {};
$jscomp.POLYFILL_PREFIX = "$jscp$";
$jscomp.IS_SYMBOL_NATIVE = "function" === typeof Symbol && "symbol" === typeof Symbol("x");
var $jscomp$lookupPolyfilledValue = function $$jscomp$lookupPolyfilledValue$($target$$, $key$$) {
  var $polyfill_polyfilledKey$$ = $jscomp.propertyToPolyfillSymbol[$key$$];
  if (null == $polyfill_polyfilledKey$$) {
    return $target$$[$key$$];
  }
  $polyfill_polyfilledKey$$ = $target$$[$polyfill_polyfilledKey$$];
  return void 0 !== $polyfill_polyfilledKey$$ ? $polyfill_polyfilledKey$$ : $target$$[$key$$];
};
$jscomp.polyfill = function $$jscomp$polyfill$($target$$, $polyfill$$, $fromLang$$, $toLang$$) {
  $polyfill$$ && ($jscomp.ISOLATE_POLYFILLS ? $jscomp.polyfillIsolated($target$$, $polyfill$$, $fromLang$$, $toLang$$) : $jscomp.polyfillUnisolated($target$$, $polyfill$$, $fromLang$$, $toLang$$));
};
$jscomp.polyfillUnisolated = function $$jscomp$polyfillUnisolated$($property$jscomp$5_split_target$$, $impl_polyfill$$, $fromLang$jscomp$1_obj$$, $i$jscomp$6_orig_toLang$$) {
  $fromLang$jscomp$1_obj$$ = $jscomp.global;
  $property$jscomp$5_split_target$$ = $property$jscomp$5_split_target$$.split(".");
  for ($i$jscomp$6_orig_toLang$$ = 0; $i$jscomp$6_orig_toLang$$ < $property$jscomp$5_split_target$$.length - 1; $i$jscomp$6_orig_toLang$$++) {
    var $key$$ = $property$jscomp$5_split_target$$[$i$jscomp$6_orig_toLang$$];
    $key$$ in $fromLang$jscomp$1_obj$$ || ($fromLang$jscomp$1_obj$$[$key$$] = {});
    $fromLang$jscomp$1_obj$$ = $fromLang$jscomp$1_obj$$[$key$$];
  }
  $property$jscomp$5_split_target$$ = $property$jscomp$5_split_target$$[$property$jscomp$5_split_target$$.length - 1];
  $i$jscomp$6_orig_toLang$$ = $fromLang$jscomp$1_obj$$[$property$jscomp$5_split_target$$];
  $impl_polyfill$$ = $impl_polyfill$$($i$jscomp$6_orig_toLang$$);
  $impl_polyfill$$ != $i$jscomp$6_orig_toLang$$ && null != $impl_polyfill$$ && $jscomp.defineProperty($fromLang$jscomp$1_obj$$, $property$jscomp$5_split_target$$, {configurable:!0, writable:!0, value:$impl_polyfill$$});
};
$jscomp.polyfillIsolated = function $$jscomp$polyfillIsolated$($isNativeClass_target$$, $impl$jscomp$1_polyfill$$, $fromLang$$, $obj$jscomp$29_root$jscomp$3_toLang$$) {
  var $property$jscomp$6_split$$ = $isNativeClass_target$$.split(".");
  $isNativeClass_target$$ = 1 === $property$jscomp$6_split$$.length;
  $obj$jscomp$29_root$jscomp$3_toLang$$ = $property$jscomp$6_split$$[0];
  $obj$jscomp$29_root$jscomp$3_toLang$$ = !$isNativeClass_target$$ && $obj$jscomp$29_root$jscomp$3_toLang$$ in $jscomp.polyfills ? $jscomp.polyfills : $jscomp.global;
  for (var $i$$ = 0; $i$$ < $property$jscomp$6_split$$.length - 1; $i$$++) {
    var $key$$ = $property$jscomp$6_split$$[$i$$];
    $key$$ in $obj$jscomp$29_root$jscomp$3_toLang$$ || ($obj$jscomp$29_root$jscomp$3_toLang$$[$key$$] = {});
    $obj$jscomp$29_root$jscomp$3_toLang$$ = $obj$jscomp$29_root$jscomp$3_toLang$$[$key$$];
  }
  $property$jscomp$6_split$$ = $property$jscomp$6_split$$[$property$jscomp$6_split$$.length - 1];
  $fromLang$$ = $jscomp.IS_SYMBOL_NATIVE && "es6" === $fromLang$$ ? $obj$jscomp$29_root$jscomp$3_toLang$$[$property$jscomp$6_split$$] : null;
  $impl$jscomp$1_polyfill$$ = $impl$jscomp$1_polyfill$$($fromLang$$);
  null != $impl$jscomp$1_polyfill$$ && ($isNativeClass_target$$ ? $jscomp.defineProperty($jscomp.polyfills, $property$jscomp$6_split$$, {configurable:!0, writable:!0, value:$impl$jscomp$1_polyfill$$}) : $impl$jscomp$1_polyfill$$ !== $fromLang$$ && ($jscomp.propertyToPolyfillSymbol[$property$jscomp$6_split$$] = $jscomp.IS_SYMBOL_NATIVE ? $jscomp.global.Symbol($property$jscomp$6_split$$) : $jscomp.POLYFILL_PREFIX + $property$jscomp$6_split$$, $property$jscomp$6_split$$ = $jscomp.propertyToPolyfillSymbol[$property$jscomp$6_split$$], 
  $jscomp.defineProperty($obj$jscomp$29_root$jscomp$3_toLang$$, $property$jscomp$6_split$$, {configurable:!0, writable:!0, value:$impl$jscomp$1_polyfill$$})));
};
$jscomp.polyfill("Object.assign", function($orig$$) {
  return $orig$$ || $jscomp.assign;
}, "es6", "es3");
$jscomp.checkStringArgs = function $$jscomp$checkStringArgs$($thisArg$$, $arg$$, $func$$) {
  if (null == $thisArg$$) {
    throw new TypeError("The 'this' value for String.prototype." + $func$$ + " must not be null or undefined");
  }
  if ($arg$$ instanceof RegExp) {
    throw new TypeError("First argument to String.prototype." + $func$$ + " must not be a regular expression");
  }
  return $thisArg$$ + "";
};
$jscomp.polyfill("String.prototype.repeat", function($orig$$) {
  return $orig$$ ? $orig$$ : function($copies$$) {
    var $string$$ = $jscomp.checkStringArgs(this, null, "repeat");
    if (0 > $copies$$ || 1342177279 < $copies$$) {
      throw new RangeError("Invalid count value");
    }
    $copies$$ |= 0;
    for (var $result$$ = ""; $copies$$;) {
      if ($copies$$ & 1 && ($result$$ += $string$$), $copies$$ >>>= 1) {
        $string$$ += $string$$;
      }
    }
    return $result$$;
  };
}, "es6", "es3");
$jscomp.stringPadding = function $$jscomp$stringPadding$($padString_padding$$, $padLength$$) {
  $padString_padding$$ = void 0 !== $padString_padding$$ ? String($padString_padding$$) : " ";
  return 0 < $padLength$$ && $padString_padding$$ ? $padString_padding$$.repeat(Math.ceil($padLength$$ / $padString_padding$$.length)).substring(0, $padLength$$) : "";
};
$jscomp.polyfill("String.prototype.padStart", function($orig$$) {
  return $orig$$ ? $orig$$ : function($targetLength$$, $opt_padString$$) {
    var $string$$ = $jscomp.checkStringArgs(this, null, "padStart");
    return $jscomp.stringPadding($opt_padString$$, $targetLength$$ - $string$$.length) + $string$$;
  };
}, "es8", "es3");
$jscomp.polyfill("Object.values", function($orig$$) {
  return $orig$$ ? $orig$$ : function($obj$$) {
    var $result$$ = [], $key$$;
    for ($key$$ in $obj$$) {
      $jscomp.owns($obj$$, $key$$) && $result$$.push($obj$$[$key$$]);
    }
    return $result$$;
  };
}, "es8", "es3");
$jscomp.polyfill("Object.entries", function($orig$$) {
  return $orig$$ ? $orig$$ : function($obj$$) {
    var $result$$ = [], $key$$;
    for ($key$$ in $obj$$) {
      $jscomp.owns($obj$$, $key$$) && $result$$.push([$key$$, $obj$$[$key$$]]);
    }
    return $result$$;
  };
}, "es8", "es3");
$jscomp.polyfill("Object.is", function($orig$$) {
  return $orig$$ ? $orig$$ : function($left$$, $right$$) {
    return $left$$ === $right$$ ? 0 !== $left$$ || 1 / $left$$ === 1 / $right$$ : $left$$ !== $left$$ && $right$$ !== $right$$;
  };
}, "es6", "es3");
$jscomp.polyfill("Array.prototype.includes", function($orig$$) {
  return $orig$$ ? $orig$$ : function($searchElement$$, $i$jscomp$8_opt_fromIndex$$) {
    var $array$$ = this;
    $array$$ instanceof String && ($array$$ = String($array$$));
    var $len$$ = $array$$.length;
    $i$jscomp$8_opt_fromIndex$$ = $i$jscomp$8_opt_fromIndex$$ || 0;
    for (0 > $i$jscomp$8_opt_fromIndex$$ && ($i$jscomp$8_opt_fromIndex$$ = Math.max($i$jscomp$8_opt_fromIndex$$ + $len$$, 0)); $i$jscomp$8_opt_fromIndex$$ < $len$$; $i$jscomp$8_opt_fromIndex$$++) {
      var $element$$ = $array$$[$i$jscomp$8_opt_fromIndex$$];
      if ($element$$ === $searchElement$$ || Object.is($element$$, $searchElement$$)) {
        return !0;
      }
    }
    return !1;
  };
}, "es7", "es3");
$jscomp.polyfill("String.prototype.includes", function($orig$$) {
  return $orig$$ ? $orig$$ : function($searchString$$, $opt_position$$) {
    return -1 !== $jscomp.checkStringArgs(this, $searchString$$, "includes").indexOf($searchString$$, $opt_position$$ || 0);
  };
}, "es6", "es3");
Object.defineProperty(exports, "__esModule", {value:!0});
var subs_1 = require("./subs"), task_1 = require("./task"), runOpKeywords = ["echo", "task", "exec"], compileOpKeywords = ["spread", "stage", "plan"], macroOpKeywords = ["def", "seq", "with"];
exports.opKeywords = [].concat($jscomp.arrayFromIterable(runOpKeywords), $jscomp.arrayFromIterable(compileOpKeywords), $jscomp.arrayFromIterable(macroOpKeywords));
var UIDGenerator = require("uid-generator"), uidgen = new UIDGenerator(32, UIDGenerator.BASE58), Op = function $Op$($keyword$$, $init$$) {
  this.name = "";
  this.options = {};
  this.args = [];
  this.ops = [];
  this.keyword = $keyword$$;
  $init$$.name && (this.name = $init$$.name);
  $init$$.options && (this.options = Object.assign({}, $init$$.options));
  $init$$.args && (this.args = [].concat($jscomp.arrayFromIterable($init$$.args)));
  $init$$.ops && (this.ops = [].concat($jscomp.arrayFromIterable($init$$.ops)));
};
Op.prototype.toJSON = function $Op$$toJSON$() {
  var $data$$ = [];
  $data$$.push(this.keyword);
  this.name ? $data$$.push(this.name) : this.options && $data$$.push(this.options);
  if (this.ops.length) {
    var $ops$$ = [];
    this.ops.forEach(function($o$$) {
      $ops$$.push($o$$.toJSON());
    });
    $data$$.push($ops$$);
  } else {
    this.args.length && $data$$.push(this.args);
  }
  return $data$$;
};
Op.prototype.compile = function $Op$$compile$($state$$) {
  return [[this], $state$$];
};
Op.prototype.substitute = function $Op$$substitute$($state$$) {
  throw Error("substitute() must be defined in Oper derived classes");
};
Op.prototype.execute = function $Op$$execute$($state$$) {
  throw Error("execute() is undefined for " + this.keyword);
};
function parseLeafArgs($d$$) {
  return [$d$$[0], {name:$d$$[1][0], options:$d$$[1][1], args:$d$$[1][2]}];
}
function parseNodeArgs($d$$) {
  return [$d$$[0], {name:$d$$[1][0], options:$d$$[1][1], ops:parseOps($d$$[1][2])}];
}
function parseOpInit($d$$) {
  switch($d$$[0]) {
    case "echo":
      return parseLeafArgs($d$$);
    case "task":
      return parseLeafArgs($d$$);
    case "exec":
      return parseLeafArgs($d$$);
    case "spread":
      return parseNodeArgs($d$$);
    case "stage":
      return parseNodeArgs($d$$);
    case "plan":
      return parseNodeArgs($d$$);
    case "def":
      return parseLeafArgs($d$$);
    case "seq":
      return parseNodeArgs($d$$);
    case "with":
      return parseNodeArgs($d$$);
    default:
      throw Error('Unknown keyword "' + $d$$[0] + '" in parseOpInit');
  }
}
function createOp($d$$) {
  switch($d$$[0]) {
    case "echo":
      return new OpEcho($d$$[1]);
    case "task":
      return new OpTask($d$$[1]);
    case "exec":
      return new OpExec($d$$[1]);
    case "spread":
      return new OpSpread($d$$[1]);
    case "stage":
      return new OpStage($d$$[1]);
    case "plan":
      return new OpPlan($d$$[1]);
    case "def":
      return new OpDef($d$$[1]);
    case "seq":
      return new OpSeq($d$$[1]);
    case "with":
      return new OpWith($d$$[1]);
    default:
      throw Error('Unknown keyword "' + $d$$[0] + '" in parseOpInit');
  }
}
function parseOps($data$$) {
  return $data$$.map(function($d$$) {
    return createOp(parseOpInit(preParse($d$$)));
  });
}
exports.parseOps = parseOps;
var OpEcho = function $OpEcho$($args$$) {
  Op.call(this, "echo", $args$$);
};
$jscomp.inherits(OpEcho, Op);
OpEcho.prototype.substitute = function $OpEcho$$substitute$($state$$) {
  return new OpEcho({name:subs_1.substitute(this.name, $state$$), options:this.options, args:this.args});
};
OpEcho.prototype.execute = function $OpEcho$$execute$($state$$) {
  $state$$ = Object.assign({}, $state$$, {taskUid:uidgen.generateSync()});
  return [[{taskId:task_1.taskId($state$$), op:"echo", cmd:this.name, args:[]}], $state$$];
};
var OpTask = function $OpTask$($args$$) {
  Op.call(this, "task", $args$$);
};
$jscomp.inherits(OpTask, Op);
OpTask.prototype.substitute = function $OpTask$$substitute$($state$$) {
  return new OpTask({name:subs_1.substitute(this.name, $state$$), options:this.options, args:subs_1.substitute(this.args, $state$$)});
};
OpTask.prototype.execute = function $OpTask$$execute$($state$jscomp$6_withState$$) {
  $state$jscomp$6_withState$$ = Object.assign({}, $state$jscomp$6_withState$$, {taskUid:uidgen.generateSync()});
  return [[{taskId:task_1.taskId($state$jscomp$6_withState$$), op:"task", cmd:this.name, args:this.args}], $state$jscomp$6_withState$$];
};
var OpExec = function $OpExec$($args$$) {
  Op.call(this, "exec", $args$$);
};
$jscomp.inherits(OpExec, Op);
OpExec.prototype.substitute = function $OpExec$$substitute$($state$$) {
  return new OpExec({name:subs_1.substitute(this.name, $state$$), options:this.options, args:subs_1.substitute(this.args, $state$$)});
};
OpExec.prototype.execute = function $OpExec$$execute$($state$jscomp$8_withState$$) {
  $state$jscomp$8_withState$$ = Object.assign({}, $state$jscomp$8_withState$$, {taskUid:uidgen.generateSync()});
  return [[{taskId:task_1.taskId($state$jscomp$8_withState$$), op:"exec", cmd:this.name, args:this.args}], $state$jscomp$8_withState$$];
};
var OpSpread = function $OpSpread$($args$$) {
  Op.call(this, "spread", $args$$);
};
$jscomp.inherits(OpSpread, Op);
OpSpread.prototype.substitute = function $OpSpread$$substitute$($state$$) {
  return new OpSpread({name:this.name, options:this.options, ops:this.ops.map(function($o$$) {
    return $o$$.substitute($state$$);
  })});
};
OpSpread.prototype.compile = function $OpSpread$$compile$($cops_state$$) {
  var $$jscomp$destructuring$var0_ste$$ = $jscomp.makeIterator(compileOps(this.ops, $cops_state$$));
  $cops_state$$ = $$jscomp$destructuring$var0_ste$$.next().value;
  $$jscomp$destructuring$var0_ste$$ = $$jscomp$destructuring$var0_ste$$.next().value;
  return [[new OpSpread({name:this.name, options:this.options, ops:$cops_state$$})], $$jscomp$destructuring$var0_ste$$];
};
OpSpread.prototype.execute = function $OpSpread$$execute$($state$$) {
  return executeOps(this.ops, $state$$);
};
var OpStage = function $OpStage$($args$$) {
  Op.call(this, "stage", $args$$);
};
$jscomp.inherits(OpStage, Op);
OpStage.prototype.substitute = function $OpStage$$substitute$($state$$) {
  return new OpStage({name:this.name, options:this.options, ops:this.ops.map(function($o$$) {
    return $o$$.substitute($state$$);
  })});
};
OpStage.prototype.compile = function $OpStage$$compile$($cops$jscomp$1_state$$) {
  var $$jscomp$destructuring$var1_ste$$ = $jscomp.makeIterator(compileOps(this.ops, $cops$jscomp$1_state$$));
  $cops$jscomp$1_state$$ = $$jscomp$destructuring$var1_ste$$.next().value;
  $$jscomp$destructuring$var1_ste$$ = $$jscomp$destructuring$var1_ste$$.next().value;
  return [[new OpStage({name:this.name, options:this.options, ops:$cops$jscomp$1_state$$})], $$jscomp$destructuring$var1_ste$$];
};
OpStage.prototype.execute = function $OpStage$$execute$($state$$) {
  var $compiled$$ = [], $withState$$ = Object.assign({}, $state$$, {stageIdx:"undefined" !== typeof $state$$.stageIdx ? $state$$.stageIdx + 1 : 0, stageUid:uidgen.generateSync(), stageName:this.name, stepIdx:0});
  this.ops.forEach(function($$jscomp$destructuring$var2_$jscomp$destructuring$var3_cops$jscomp$2_o$$) {
    $$jscomp$destructuring$var2_$jscomp$destructuring$var3_cops$jscomp$2_o$$ = $jscomp.makeIterator(executeOps([$$jscomp$destructuring$var2_$jscomp$destructuring$var3_cops$jscomp$2_o$$], $withState$$));
    $$jscomp$destructuring$var2_$jscomp$destructuring$var3_cops$jscomp$2_o$$ = $jscomp.makeIterator($$jscomp$destructuring$var2_$jscomp$destructuring$var3_cops$jscomp$2_o$$.next().value);
    $$jscomp$destructuring$var2_$jscomp$destructuring$var3_cops$jscomp$2_o$$ = $jscomp.arrayFromIterator($$jscomp$destructuring$var2_$jscomp$destructuring$var3_cops$jscomp$2_o$$);
    0 < $$jscomp$destructuring$var2_$jscomp$destructuring$var3_cops$jscomp$2_o$$.length && ($compiled$$ = $compiled$$.concat($$jscomp$destructuring$var2_$jscomp$destructuring$var3_cops$jscomp$2_o$$));
    $withState$$.stepIdx++;
  });
  return [$compiled$$, $withState$$];
};
var OpPlan = function $OpPlan$($args$$) {
  Op.call(this, "plan", $args$$);
};
$jscomp.inherits(OpPlan, Op);
OpPlan.prototype.substitute = function $OpPlan$$substitute$($state$$) {
  return new OpPlan({name:this.name, ops:this.ops.map(function($o$$) {
    return $o$$.substitute($state$$);
  })});
};
OpPlan.prototype.compile = function $OpPlan$$compile$($cops$jscomp$3_state$$) {
  var $$jscomp$destructuring$var4_ste$$ = $jscomp.makeIterator(compileOps(this.ops, $cops$jscomp$3_state$$));
  $cops$jscomp$3_state$$ = $$jscomp$destructuring$var4_ste$$.next().value;
  $$jscomp$destructuring$var4_ste$$ = $$jscomp$destructuring$var4_ste$$.next().value;
  return [[new OpPlan({name:this.name, options:this.options, ops:$cops$jscomp$3_state$$})], $$jscomp$destructuring$var4_ste$$];
};
OpPlan.prototype.execute = function $OpPlan$$execute$($state$$) {
  $state$$ = Object.assign($state$$, {planName:this.name, planUid:uidgen.generateSync()});
  return executeOps(this.ops, $state$$);
};
var OpDef = function $OpDef$($args$$) {
  Op.call(this, "def", $args$$);
};
$jscomp.inherits(OpDef, Op);
OpDef.prototype.substitute = function $OpDef$$substitute$() {
  return new OpDef({options:this.options});
};
OpDef.prototype.compile = function $OpDef$$compile$($state$$) {
  return [[], Object.assign({}, $state$$, this.options)];
};
var OpSeq = function $OpSeq$($args$$) {
  Op.call(this, "seq", $args$$);
};
$jscomp.inherits(OpSeq, Op);
OpSeq.prototype.substitute = function $OpSeq$$substitute$($state$$) {
  return new OpSeq({options:this.options, name:subs_1.substitute(this.name, $state$$), ops:this.ops.map(function($o$$) {
    return $o$$.substitute($state$$);
  })});
};
OpSeq.prototype.compile = function $OpSeq$$compile$($state$$) {
  var $compiled$$ = [], $$jscomp$destructuring$var5_i$$ = seqOpts(this.name ? this.name : this.options), $end$$ = $$jscomp$destructuring$var5_i$$.end, $by$$ = $$jscomp$destructuring$var5_i$$.by;
  for ($$jscomp$destructuring$var5_i$$ = $$jscomp$destructuring$var5_i$$.start; $$jscomp$destructuring$var5_i$$ <= $end$$; $$jscomp$destructuring$var5_i$$ += $by$$) {
    var $cops$jscomp$4_withState$$ = Object.assign({}, $state$$, {X:("" + $$jscomp$destructuring$var5_i$$).padStart(5, "0"), I:$$jscomp$destructuring$var5_i$$});
    $cops$jscomp$4_withState$$ = $jscomp.makeIterator(compileOps(this.ops, $cops$jscomp$4_withState$$)).next().value;
    0 < $cops$jscomp$4_withState$$.length && ($compiled$$ = $compiled$$.concat($cops$jscomp$4_withState$$));
  }
  return [$compiled$$, $state$$];
};
function seqOpts($o$$) {
  var $start$$ = 0, $end$$, $by$$ = 1;
  if ("object" === typeof $o$$) {
    "undefined" !== typeof $o$$.start && ($start$$ = $o$$.start), "undefined" !== typeof $o$$.end ? $end$$ = $o$$.end : "undefined" !== typeof $o$$.count && ($end$$ = $start$$ + ($o$$.count - 1)), "undefined" !== typeof $o$$.by && ($by$$ = $o$$.by);
  } else {
    if ("string" !== typeof $o$$) {
      throw Error("seq requires either an options object or string initializer");
    }
    $o$$ = $o$$.split(" ");
    1 === $o$$.length ? $end$$ = $start$$ + (parseInt($o$$[0], 10) - 1) : 2 <= $o$$.length && ($start$$ = parseInt($o$$[0], 10), $end$$ = parseInt($o$$[1], 10), 3 === $o$$.length && ($by$$ = parseInt($o$$[2], 10)));
  }
  return {start:$start$$, end:$end$$, by:$by$$};
}
exports.seqOpts = seqOpts;
var OpWith = function $OpWith$($args$$) {
  Op.call(this, "with", $args$$);
};
$jscomp.inherits(OpWith, Op);
OpWith.prototype.substitute = function $OpWith$$substitute$($state$$) {
  return new OpWith({options:subs_1.substitute(this.options, $state$$), ops:this.ops.map(function($o$$) {
    return $o$$.substitute($state$$);
  })});
};
OpWith.prototype.compile = function $OpWith$$compile$($state$$) {
  var $compiled$$ = [], $n$$ = 0;
  Object.values(this.options).map(function($v$$) {
    if (!Array.isArray($v$$)) {
      throw Error("with operation expects an options object with only array values");
    }
    $n$$ = Math.max($n$$, $v$$.length);
  });
  var $i$$ = 0, $$jscomp$loop$0$$ = {};
  for ($i$$ = 0; $i$$ < $n$$; $$jscomp$loop$0$$ = {$jscomp$loop$prop$withState$1:$$jscomp$loop$0$$.$jscomp$loop$prop$withState$1}, $i$$++) {
    $$jscomp$loop$0$$.$jscomp$loop$prop$withState$1 = Object.assign({}, $state$$);
    Object.entries(this.options).map(function($$jscomp$loop$0$$) {
      return function($e$$) {
        Array.isArray($e$$[1]) ? $$jscomp$loop$0$$.$jscomp$loop$prop$withState$1[$e$$[0]] = $e$$[1][$i$$ % $e$$[1].length] : $$jscomp$loop$0$$.$jscomp$loop$prop$withState$1[$e$$[0]] = $e$$[1];
      };
    }($$jscomp$loop$0$$));
    var $cops$$ = $jscomp.makeIterator(compileOps(this.ops, $$jscomp$loop$0$$.$jscomp$loop$prop$withState$1)).next().value;
    0 < $cops$$.length && ($compiled$$ = $compiled$$.concat($cops$$));
  }
  return [$compiled$$, $state$$];
};
function compileOps($ops$$, $state$$) {
  var $compiled$$ = [], $withState$$ = $state$$;
  $ops$$.forEach(function($cops$jscomp$6_o$$) {
    var $$jscomp$destructuring$var8_ste$$ = $jscomp.makeIterator($cops$jscomp$6_o$$.substitute($withState$$).compile($withState$$));
    $cops$jscomp$6_o$$ = $$jscomp$destructuring$var8_ste$$.next().value;
    $$jscomp$destructuring$var8_ste$$ = $$jscomp$destructuring$var8_ste$$.next().value;
    $compiled$$ = $compiled$$.concat($cops$jscomp$6_o$$);
    $withState$$ = $$jscomp$destructuring$var8_ste$$;
  });
  return [$compiled$$, $withState$$];
}
exports.compileOps = compileOps;
function executeOps($ops$$, $state$$) {
  var $executed$$ = [], $withState$$ = Object.assign({}, $state$$);
  $ops$$.forEach(function($o$jscomp$9_ops$$) {
    var $$jscomp$destructuring$var9_ste$$ = $jscomp.makeIterator($o$jscomp$9_ops$$.substitute($withState$$).execute($withState$$));
    $o$jscomp$9_ops$$ = $$jscomp$destructuring$var9_ste$$.next().value;
    $$jscomp$destructuring$var9_ste$$ = $$jscomp$destructuring$var9_ste$$.next().value;
    $executed$$ = $executed$$.concat($o$jscomp$9_ops$$);
    $withState$$ = $$jscomp$destructuring$var9_ste$$;
  });
  return [$executed$$, $withState$$];
}
exports.executeOps = executeOps;
function preParse($op$$) {
  if (!Array.isArray($op$$) || 2 > $op$$.length || 3 < $op$$.length) {
    throw Error("Oper parse data must be an array with 2 or 3 elements");
  }
  if ("string" !== typeof $op$$[0] || !exports.opKeywords.includes($op$$[0])) {
    throw Error('First element of oper parse data must ba a keyword string. Received "' + $op$$[0]);
  }
  if (3 === $op$$.length && !Array.isArray($op$$[2])) {
    throw Error("Third element of oper parse data must be an array");
  }
  if ("string" !== typeof $op$$[1] && "object" !== typeof $op$$[1] && !Array.isArray($op$$[1])) {
    throw Error("Second element of oper parse data must be a string, object, or array");
  }
  if (Array.isArray($op$$[1]) && 3 === $op$$.length) {
    throw Error("Array element of oper parse data must be the last element");
  }
  for (var $name$$ = "", $options$$ = {}, $ops$$ = [], $data$$ = [].concat($jscomp.arrayFromIterable($op$$)), $keyword$$ = $data$$.shift(), $d$$; $d$$ = $data$$.shift();) {
    if (Array.isArray($d$$)) {
      $ops$$ = [].concat($jscomp.arrayFromIterable($d$$));
    } else {
      if ("object" === typeof $d$$) {
        $options$$ = Object.assign({}, $d$$);
      } else {
        if ("string" !== typeof $d$$) {
          throw Error("Unexpected data in parseOpArgs = " + JSON.stringify($op$$));
        }
        $name$$ = $d$$;
      }
    }
  }
  return [$keyword$$, [$name$$, $options$$, $ops$$]];
}
;
