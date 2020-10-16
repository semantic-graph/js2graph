artifacts = [
    "com.ibm.wala.core",
    "com.ibm.wala.cast.js",
    "com.ibm.wala.cast",
    "com.ibm.wala.cast.js.rhino",
    "com.ibm.wala.cast.js.nodejs",
    "com.ibm.wala.util",
    "com.ibm.wala.shrike" ]

version = "1.5.4"

for artifact_id in artifacts:
    print("mvn install:install-file "
        f"-Dfile={artifact_id}-{version}.jar "
        f"-Dsources={artifact_id}-{version}-sources.jar "
        f"-Djavadoc={artifact_id}-{version}-javadoc.jar "
        "-DgroupId=com.github.wala "
        f"-DartifactId={artifact_id} "
        f"-Dversion={version} "
        "-DgeneratePom=true "
        "-Dpackaging=jar")