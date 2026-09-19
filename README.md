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

The whole server (client + server + SDKs) can be built with nix:

```
nix build .#default
```

This produces `result/bin/javap-server` plus the SDK manifest at `result/sdk/sdks.json`, matching the default
(relative) `sdkManifest` config value, so `result/bin/javap-server config.json` works out of the box from `result`.

`server` and `sdks` are also available as separate outputs, for deployments that manage the SDK closure's lifecycle
independently from the server -- e.g. rebuilding/redeploying the server on every commit while keeping the (large,
rarely-changing) SDKs pinned separately. Point such a deployment's `sdkManifest` config value at the `sdks` output's
`sdks.json` directly:

```
nix build .#sdks -o sdk
```

After bumping a gradle or yarn dependency, refresh the pinned offline dependency cache (`nix/deps.json` and the
yarn hash in `flake.nix`) with:

```
nix run .#update-deps
```

dev, using a JDK from `nix develop` (or any JDK 21):

```
nix develop --command bash -c '
  ./gradlew clean installShadowDist &&
  ./gradlew :server:run --args="config.json"
'
```

prod:

```
./gradlew clean installShadowDist &&
java -jar server/build/install/server-shadow/lib/server-1.0-SNAPSHOT-shaded.jar config.json
```
