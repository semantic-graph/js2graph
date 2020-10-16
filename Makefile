GITHASH := $(shell git rev-parse --short=8 --verify HEAD)

JAR := target/js2graph-1.0-SNAPSHOT-jar-with-dependencies.jar
GITHASH_FILE := $(JAR).gitver

jar: $(JAR) update-gitver

all: jar

.phony: init test test-resource check jar

check:
	./check-jdk-version

init: check
	cd local_lib; bash install.sh
	mvn -q package
	mvn -q clean compile assembly:single

SRC_FILES := $(shell find src/ -type f -name '*.scala') $(shell find src/ -type f -name '*.java') pom.xml

update-gitver:
	echo $(GITHASH) > $(GITHASH_FILE)

# -B for batch mode
$(JAR): $(SRC_FILES)
	mvn -q -B compile assembly:single -o

record-junit:
	RECORD=1 mvn -q test

junit:
	mvn -q test

test: junit test-js-e2e

test-js-e2e: jar
	timeout 300 ./js2graph tests/tmpoc4jukbq.js tests/tmpoc4jukbq.js.gexf

clean:
	rm -r target
	rm -r src/test/resources/*.class

javadoc:
	mvn javadoc:javadoc

check-format:
	scalafmt --check src/main/scala

format:
	scalafmt src/main/scala
