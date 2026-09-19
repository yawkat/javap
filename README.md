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

The server (client + server, packaged as `bin/javap-server`) can be built with nix:

```
nix build .#default
```

This does *not* build the SDKs -- `javap-server` needs those separately, via the SDK manifest `sdk/sdks.json`
(the location can be changed with `sdkManifest` in the config file):

```
nix build .#sdks -o sdk
```

They're separate outputs because they change independently: the SDKs are large, expensive to rebuild, and rarely
change, while the server rebuilds on every commit. A deployment needs both, typically wiring `sdkManifest` in its
config to the `sdks` output's store path rather than relying on the relative-path default.

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
