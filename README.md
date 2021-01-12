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
./gradlew clean installShadowDist &&
./gradlew :server:run --args="config.json"
```

prod:

```
./gradlew clean installShadowDist &&
java -jar server/build/install/server-shadow/lib/server-1.0-SNAPSHOT-shaded.jar config.json
```
