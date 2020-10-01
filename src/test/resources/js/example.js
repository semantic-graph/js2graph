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

h();
