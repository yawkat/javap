javap [![Build Status](https://ci.yawk.at/job/javap/badge/icon)](https://ci.yawk.at/job/javap)
=====

javap pastebin for viewing the bytecode of small pieces of code.

MPL 2.0 licensed.

Prerequisites
----

- PostgreSQL server with a `javap` role, owner of a `javap` database 
- [Nix](https://nixos.org/) with flakes. The SDKs (JDKs, ECJ, Kotlin, Scala) and their metadata are defined in
  `nix/sdks.nix`, and the build evaluates that metadata.

Build
-----

Build the SDKs. This creates the SDK manifest `sdk/sdks.json` (the location can be changed with `sdkManifest` in the
config file):

```
nix build .#sdks -o sdk
```

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
