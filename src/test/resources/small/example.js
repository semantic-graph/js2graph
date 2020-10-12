function main() {
  function f(x) {
    return x;
  }

  function g(x, y) {
    return x + y;
  }

  function h() {
    var x = f(1);
    var y = g(2, 3);
    return x + y;
  }

  console.log(h());
}

// NOTE: need to put inside a main to ensure that getReturnFlowFunction will process h's exit to its call-site.
// It might be a bug...I don't quite understand what is going on.
main();