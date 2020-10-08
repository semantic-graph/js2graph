# js2graph

[![Build Status](https://travis-ci.com/semantic-graph/js2graph.svg?branch=master)](https://travis-ci.com/semantic-graph/js2graph)

Extract semantic graph from JavaScript.

## Usage

    ./seguardjs-cli path/to/filename.js path/to/output.js.gexf

## Test

### Unit Tests

```
mvn -q test
```

Test files:

- `src/test/scala/edu/washington/cs/seguard/JsTest.scala`

### End2end Tests

```
make test-java-e2e
```

### Code formatting

Install `scalafmt` (version 2.7.4): https://scalameta.org/scalafmt/docs/installation.html#cli. Then

```
# Format code
make format
# Check format
make check-format
```

## Visualize the output

Transform the output to dot graph first ():

```
# https://github.com/semantic-graph/semantic-graph.py/blob/master/render-gexf.py
python render-gexf.py path/to/example2.gexf
# Find output in path/to/example2.dot
```

Then use `dot` or https://dreampuf.github.io/GraphvizOnline for visualize.

## Useful WALA resources

- [prologue.js](https://github.com/wala/WALA/blob/master/com.ibm.wala.cast.js/src/main/resources/prologue.js)