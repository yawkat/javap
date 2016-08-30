javap
=====

javap pastebin for viewing the bytecode of small pieces of code.

MPL 2.0 licensed.

Build
-----

dev:

```
mvn -pl shared,server,client install &&
mvn -pl server-bootstrap exec:java -Dexec.mainClass=at.yawk.javap.JavapApplicationKt -Dexec.args="server config.yml"
```

prod:

```
mvn clean install &&
java -jar server-bootstrap/target/javap-server-bootstrap-1.0-SNAPSHOT-shaded.jar server config.yml
```
