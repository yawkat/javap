{
  description = "javap pastebin";

  inputs.nixpkgs.url = "github:NixOS/nixpkgs/nixos-unstable";

  outputs = { self, nixpkgs }:
    let
      # the SDKs are prebuilt x86_64 binaries
      system = "x86_64-linux";
      pkgs = nixpkgs.legacyPackages.${system};
      sdks = import ./nix/sdks.nix { inherit pkgs; };

      jdk = pkgs.jdk21;
      gradle = pkgs.gradle_9.override { java = jdk; };

      # shared/build.gradle.kts generates SdkMetadata.kt by shelling out to `nix eval .#sdkMetadata`. That's not
      # available (no network, no nix) inside the sandboxed nix build, so it's replaced with a stub that prints the
      # metadata evaluated above -- the same source of truth `sdks.manifest` is built from, so the two can't drift.
      fakeNix = pkgs.writeShellScriptBin "nix" ''
        exec cat ${pkgs.writeText "sdk-metadata.json" (builtins.toJSON sdks.metadata)}
      '';

      javap = pkgs.stdenv.mkDerivation (finalAttrs: rec {
        pname = "javap";
        version = "rolling-${self.shortRev or "dirty"}";

        src = self;

        nativeBuildInputs = [
          gradle
          jdk
          pkgs.makeBinaryWrapper
          pkgs.nodejs_22
          pkgs.yarn
          pkgs.fixup-yarn-lock
          fakeNix
        ];

        # `nix run .#update-deps` refreshes this after a dependency bump.
        mitmCache = gradle.fetchDeps {
          pkg = finalAttrs.finalPackage;
          data = ./nix/deps.json;
        };

        yarnOfflineCache = pkgs.fetchYarnDeps {
          yarnLock = finalAttrs.src + "/kotlin-js-store/yarn.lock";
          hash = "sha256-sNmPRP4srP7+PLF6G7mGlhiJ2krSr8CQyk1/flv4kh4=";
        };

        gradleFlags = [ "-Dorg.gradle.welcome=never" "--no-daemon" ];
        gradleUpdateTask = ":server:javapNixDownloadDeps";

        # Picked up by build.gradle.kts to point the kotlin-js plugins at prebuilt node/yarn instead of letting
        # them download their own, which the sandboxed build has no network access to do.
        NIX_NODEJS_BIN = "${pkgs.nodejs_22}/bin/node";
        NIX_YARN_BIN = "${pkgs.yarn}/bin/yarn";

        postPatch = ''
          fixup-yarn-lock kotlin-js-store/yarn.lock
        '';

        buildPhase = ''
          runHook preBuild

          export HOME=$(mktemp -d)
          yarn config --offline set yarn-offline-mirror ${finalAttrs.yarnOfflineCache}
          echo '--install.offline true' >> "$HOME/.yarnrc"

          gradle installShadowDist $gradleFlags

          runHook postBuild
        '';

        installPhase = ''
          runHook preInstall

          mkdir -p $out/bin
          mkdir -p $out/share/javap
          cp -r server/build/install/server-shadow/lib/server-1.0-SNAPSHOT-shaded.jar $out/share/javap/javap-server.jar

          makeWrapper ${pkgs.jre}/bin/java $out/bin/javap-server \
            --add-flags "-Xmx256M -jar $out/share/javap/javap-server.jar"

          runHook postInstall
        '';
      });
    in
    {
      # SDK metadata, evaluated by gradle at build time. Does not require building any SDK.
      sdkMetadata = sdks.metadata;

      packages.${system} = {
        # SDK manifest used by the server at runtime: `nix build .#sdks -o sdk`
        sdks = sdks.manifest;
        server = javap;

        # javap + sdks.manifest at `sdk/sdks.json`, matching the default (relative) `sdkManifest` config value, so
        # `nix build; result/bin/javap-server config.json` works out of the box. Deployments that manage the SDK
        # closure's lifecycle separately from the server (e.g. rebuilding the server far more often) should use the
        # `server` and `sdks` outputs directly and wire `sdkManifest` themselves instead.
        default = pkgs.symlinkJoin {
          name = "javap";
          paths = [ javap ];
          postBuild = ''
            mkdir -p $out/sdk
            ln -s ${sdks.manifest}/sdks.json $out/sdk/sdks.json
          '';
        };
      };

      apps.${system}.update-deps = {
        type = "app";
        program = "${javap.mitmCache.updateScript}";
      };

      devShells.${system}.default = pkgs.mkShell {
        # Provides a JDK matching what the nix package build uses, for running `./gradlew` directly (dev loop, CI).
        packages = [ jdk ];
      };
    };
}
