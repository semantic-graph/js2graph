/*
Expected output:

  1 -> console.log

 */

function f() {
  return 1
}

function g() {
  return f() + 1;
}

function h() {
  var x = f();
  var y = g();
  var z = x + y;
  return z;
}

/*

IR of this invocation in WALA:

  12   v21 = global:global console             example.js [188->195] (line 23) [21=[$$destructure$rcvr4]]
  13   check v21                               example.js [188->195] (line 23) [21=[$$destructure$rcvr4]]
  BB2
  16   v25 = global:global h                   example.js [200->201] (line 23)
  17   check v25                               example.js [200->201] (line 23)
  BB3
  18   v27 = global:global __WALA__int3rnal__globalexample.js [200->203] (line 23)
  19   v24 = invoke v25@19 v27 exception:v28   example.js [200->203] (line 23)
  20   v23 = dispatch v22:#log@20 v21,v24 exception:v29example.js [188->204] (line 23) [22=[$$destructure$elt4]21=[$$destructure$rcvr4]]
 */
console.log(h());
