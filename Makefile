GITHASH := $(shell git rev-parse --short=8 --verify HEAD)

JAR := target/js2graph-1.0-SNAPSHOT-jar-with-dependencies.jar
GITHASH_FILE := $(JAR).gitver

jar: $(JAR) update-gitver

all: jar

.phony: init test test-resource check jar

check:
	./check-jdk-version

init: check
	mvn -q package
	mvn -q clean compile assembly:single

SRC_FILES := $(shell find src/ -type f -name '*.scala') $(shell find src/ -type f -name '*.java') pom.xml

update-gitver:
	echo $(GITHASH) > $(GITHASH_FILE)

# -B for batch mode
$(JAR): $(SRC_FILES)
	mvn -q -B compile assembly:single -o

regtest-not-record:
	grep 'private val record = false' src/test/scala/edu/washington/cs/js2graph/JsTest.scala

unit-test:
	mvn -q test

test: unit-test test-js-e2e

test-js-e2e: regtest-not-record jar
	timeout 300 ./js2graph tests/tmpoc4jukbq.js tests/tmpoc4jukbq.js.gexf

clean:
	rm -r target
	rm -r src/test/resources/*.class

javadoc:
	mvn javadoc:javadoc
