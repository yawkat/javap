javap [![Build Status](https://ci.yawk.at/job/javap/badge/icon)](https://ci.yawk.at/job/javap)
=====

javap pastebin for viewing the bytecode of small pieces of code.

MPL 2.0 licensed.

Prerequisites
----

- PostgreSQL server with a `javap` role, owner of a `javap` database 

Build
-----

dev:

```
mvn -pl shared,shared-js,server,client install &&
mvn -pl server compile exec:java -Dexec.mainClass=at.yawk.javap.JavapApplicationKt -Dexec.args="server config.yml"
```

prod:

```
mvn clean install &&
java -jar server/target/javap-server-1.0-SNAPSHOT-shaded.jar server config.yml
```
