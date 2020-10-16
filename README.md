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

**UPDATE**: You can also use this script for an end-to-end PDF rendering:

```
scripts/viz path/to/js
```

## Useful WALA resources

- [prologue.js](https://github.com/wala/WALA/blob/master/com.ibm.wala.cast.js/src/main/resources/prologue.js)

## Compile WALA

https://github.com/wala/WALA/blob/master/README-Gradle.md

Needs to use this customized version: https://github.com/izgzhen/WALA/tree/compile-nodejs-jars

Needs to run on Linux workstation:

```
./gradlew assemble processTestResources
```

Each sub-project has its own build folder:

```
./com.ibm.wala.cast.js.rhino/build/libs/com.ibm.wala.cast.js.rhino-1.5.4-sources.jar
```

Copy the used jars:

```
cp com.ibm.wala.core/build/libs/*.jar local_lib
cp com.ibm.wala.cast.js/build/libs/*.jar local_lib
cp com.ibm.wala.cast/build/libs/*.jar local_lib
cp com.ibm.wala.cast.js.rhino/build/libs/*.jar local_lib
cp com.ibm.wala.cast.js.nodejs/build/libs/*.jar local_lib
cp com.ibm.wala.util/build/libs/*.jar local_lib
cp com.ibm.wala.shrike/build/libs/*.jar local_lib
```

And then execute script `install.sh` inside `local_lib`
