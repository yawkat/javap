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

        postPatch = ''
          substituteInPlace build.gradle.kts \
            --replace-fail 'allprojects {' 'import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsPlugin
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootEnvSpec
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension

plugins.withType<NodeJsPlugin> {
    extensions.findByType<NodeJsEnvSpec>()?.let {
        it.download.set(false)
        it.command.set("${pkgs.nodejs_22}/bin/node")
    }
}

rootProject.plugins.withType<NodeJsRootPlugin> {
    rootProject.extensions.findByType<NodeJsRootExtension>()?.let {
        it.withGroovyBuilder {
            "setDownload"(false)
            "setNodeCommand"("${pkgs.nodejs_22}/bin/node")
        }
    }
}

plugins.withType<YarnPlugin> {
    extensions.findByType<YarnRootEnvSpec>()?.let {
        it.download.set(false)
        it.command.set("${pkgs.yarn}/bin/yarn")
    }
}

project(":server") {
    tasks.register("javapNixDownloadDeps") {
        dependsOn(
            ":server:compileKotlin",
            ":shared:compileKotlinJvm",
            ":shared:compileKotlinJs",
            ":client:compileKotlinJs",
        )
        doLast {
            configurations.getByName("runtimeClasspath").resolve()
        }
    }
}

allprojects {'

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
        default = javap;
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
