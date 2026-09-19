# SDK definitions for javap.
#
# Every SDK is an attrset with two parts:
#
# - `meta`: metadata needed by the client and server to present and configure the SDK. `meta.compiler` is the
#   option dialect (javac, ecj, kotlinc or scalac), not a description of how the SDK is packaged. Evaluating `meta`
#   must not require building anything, since gradle evaluates it at build time.
# - `runtime`: how the server invokes the SDK. `compiler` is the base compiler command (the server appends
#   `-d <out>`, the configured options and the input file), `compilerWithLombok` is an alternative command used when
#   lombok is enabled (or null if lombok is unsupported), `javap` is the javap command, and `env` is the environment.
#   The server may read all store paths in the closure of the runtime definition.
#
# The helper functions below are only conveniences for defining SDKs, nothing outside this file relies on them.
{ pkgs }:
let
  inherit (pkgs) lib;

  fetch = pkgs.fetchurl;

  # Unpack an archive into the store as-is. No fixup, so that prebuilt binaries and scripts are not patched.
  unpack = name: src: pkgs.stdenvNoCC.mkDerivation {
    name = lib.strings.sanitizeDerivationName name;
    inherit src;
    nativeBuildInputs = [ pkgs.unzip ];
    dontConfigure = true;
    dontBuild = true;
    dontFixup = true;
    installPhase = ''
      runHook preInstall
      cp -r . $out
      if [ -d $out/bin ]; then chmod +x $out/bin/*; fi
      runHook postInstall
    '';
  };

  maven = group: artifact: version: hash: fetch {
    url = "https://repo1.maven.org/maven2/${lib.replaceStrings [ "." ] [ "/" ] group}/${artifact}/${version}/${artifact}-${version}.jar";
    inherit hash;
  };

  lombok1_18_34 = maven "org.projectlombok" "lombok" "1.18.34" "sha256-wn1rKv9WJB0bB/y8xrGDcJ5rQyyA9zdO6x2CPobUuBo=";
  lombok1_18_18 = maven "org.projectlombok" "lombok" "1.18.18" "sha256-YB7EYgbg+crCwFg7M1DnnwlUGcOV6ZHHYWQPkpA46cw=";
  lombok1_18_4 = maven "org.projectlombok" "lombok" "1.18.4" "sha256-OfOSLetnmxhSr1GesicVfvLdCiHuw1QsjOG0Xy3zl0I=";

  # javac from a prebuilt JDK distribution
  openjdk =
    { release
    , name ? "OpenJDK ${toString release}"
    , aliases ? [ ]
    , url
    , hash
    , libPaths ? [ "lib" ]
    , lombok ? null
    , lombokArgs ? [ ]
    , supportedWarnings
    }:
    let
      jdk = unpack name (fetch { inherit url hash; });
    in
    rec {
      meta = {
        compiler = "javac";
        release = toString release;
        hasLombok = lombok != null;
        inherit name aliases supportedWarnings;
      };
      runtime = {
        compiler = [ "${jdk}/bin/javac" "-encoding" "utf-8" ];
        compilerWithLombok =
          if lombok == null then null
          else runtime.compiler ++ [
            "-cp"
            "${lombok}"
            "-processor"
            "lombok.launch.AnnotationProcessorHider$AnnotationProcessor,lombok.launch.AnnotationProcessorHider$ClaimingProcessor"
          ] ++ lombokArgs;
        javap = [ "${jdk}/bin/javap" ];
        env = {
          JAVA_HOME = "${jdk}";
          LD_LIBRARY_PATH = lib.concatMapStringsSep ":" (p: "${jdk}/${p}") libPaths;
        };
      };
    };

  # Runtime of an SDK that runs on some JDK, sharing its javap and environment
  hostedOn = hostJdk: runtime: {
    inherit (hostJdk.runtime) javap env;
    compilerWithLombok = null;
  } // runtime;

  java = hostJdk: "${hostJdk.runtime.env.JAVA_HOME}/bin/java";

  openjdk6 = openjdk {
    release = 6;
    aliases = [ "OpenJDK 6u79" ];
    url = "https://cdn.azul.com/zulu/bin/zulu6.22.0.3-jdk6.0.119-linux_x64.tar.gz";
    hash = "sha256-3UdltF2fH8MZpuW+C7x5NxWC2qj9d4ctUQcvFt7KJiU=";
    libPaths = [ "lib/amd64" "lib/amd64/jli" ];
    lombok = lombok1_18_4;
    supportedWarnings = [
      "cast" "deprecation" "divzero" "empty" "unchecked" "fallthrough" "path" "serial" "finally" "overrides"
    ];
  };
  openjdk7 = openjdk {
    release = 7;
    aliases = [ "OpenJDK 7u101" ];
    url = "https://cdn.azul.com/zulu/bin/zulu7.56.0.11-ca-jdk7.0.352-linux_x64.tar.gz";
    hash = "sha256-inOHwe0VFHQwG2VTxgRvhl3GweGJC88QaswngMVXJ8g=";
    libPaths = [ "lib/amd64" "lib/amd64/jli" ];
    lombok = lombok1_18_18;
    supportedWarnings = openjdk6.meta.supportedWarnings ++ [
      "classfile" "dep-ann" "options" "processing" "rawtypes" "static" "try" "varargs"
    ];
  };
  openjdk8 = openjdk {
    release = 8;
    aliases = [ "OpenJDK 8u92" ];
    url = "https://github.com/adoptium/temurin8-binaries/releases/download/jdk8u422-b05/OpenJDK8U-jdk_x64_linux_hotspot_8u422b05.tar.gz";
    hash = "sha256-TGBW9hZ/rnOs58MIC3iUC+XIfVT1sIiUs1F+7Ry7LAY=";
    libPaths = [ "lib/amd64" "lib/amd64/jli" ];
    lombok = lombok1_18_18;
    supportedWarnings = openjdk7.meta.supportedWarnings ++ [ "auxiliaryclass" "overloads" ];
  };
  openjdk9 = openjdk {
    release = 9;
    aliases = [ "OpenJDK 9.0.0" ];
    url = "https://github.com/AdoptOpenJDK/openjdk9-binaries/releases/download/jdk-9%2B181/OpenJDK9U-jdk_x64_linux_hotspot_9_181.tar.gz";
    hash = "sha256-6+HqrXNYT3CrTlh6uTZ/sVr4IHRZeeaDUP6A+t9hXfg=";
    lombok = lombok1_18_18;
    supportedWarnings = openjdk8.meta.supportedWarnings ++ [
      "exports" "module" "opens" "removal" "requires-automatic" "requires-transitive-automatic"
    ];
  };
  openjdk10 = openjdk {
    release = 10;
    url = "https://github.com/AdoptOpenJDK/openjdk10-binaries/releases/download/jdk-10.0.2%2B13.1/OpenJDK10U-jdk_x64_linux_hotspot_10.0.2_13.tar.gz";
    hash = "sha256-OZjDbH/rS7elZbPTNgnsZ6zUDxrlrd8QM3jy7zKrN38=";
    lombok = lombok1_18_18;
    supportedWarnings = openjdk9.meta.supportedWarnings;
  };
  openjdk11 = openjdk {
    release = 11;
    url = "https://github.com/adoptium/temurin11-binaries/releases/download/jdk-11.0.24%2B8/OpenJDK11U-jdk_x64_linux_hotspot_11.0.24_8.tar.gz";
    hash = "sha256-DnGgFWOlx7mYihaLDEznIKbf+WazwnuynR3tRh/3HQ4=";
    lombok = lombok1_18_18;
    supportedWarnings = openjdk10.meta.supportedWarnings ++ [ "preview" ];
  };
  openjdk12 = openjdk {
    release = 12;
    aliases = [ "OpenJDK 12.0.2" ];
    url = "https://github.com/AdoptOpenJDK/openjdk12-binaries/releases/download/jdk-12.0.2%2B10/OpenJDK12U-jdk_x64_linux_hotspot_12.0.2_10.tar.gz";
    hash = "sha256-EgL1NphMKNaGgdUSB6hLbHblmYV5Ey0/4bgIWqal8h4=";
    lombok = lombok1_18_18;
    supportedWarnings = openjdk11.meta.supportedWarnings ++ [ "text-blocks" ];
  };
  openjdk13 = openjdk {
    release = 13;
    url = "https://github.com/AdoptOpenJDK/openjdk13-binaries/releases/download/jdk-13.0.2%2B8/OpenJDK13U-jdk_x64_linux_hotspot_13.0.2_8.tar.gz";
    hash = "sha256-nMwGNWnxmJn9COQUZvjEzU4FBYq9tRePo3TLNl3PWZg=";
    lombok = lombok1_18_18;
    supportedWarnings = openjdk12.meta.supportedWarnings;
  };
  openjdk14 = openjdk {
    release = 14;
    url = "https://github.com/AdoptOpenJDK/openjdk14-binaries/releases/download/jdk-14.0.2%2B12/OpenJDK14U-jdk_x64_linux_hotspot_14.0.2_12.tar.gz";
    hash = "sha256-fV7n4GkJuKmcDQKfUS9nsJJZeqWw54wQm9WUBbv6dP4=";
    lombok = lombok1_18_18;
    supportedWarnings = openjdk13.meta.supportedWarnings;
  };
  openjdk15 = openjdk {
    release = 15;
    url = "https://github.com/AdoptOpenJDK/openjdk15-binaries/releases/download/jdk-15.0.2%2B7/OpenJDK15U-jdk_x64_linux_hotspot_15.0.2_7.tar.gz";
    hash = "sha256-lPIMqOqXdzVxSS5iJWOIO4hpQ4oBXQLfYCgYDdmswk0=";
    lombok = lombok1_18_18;
    supportedWarnings = openjdk14.meta.supportedWarnings;
  };
  openjdk16 = openjdk {
    release = 16;
    url = "https://github.com/adoptium/temurin16-binaries/releases/download/jdk-16.0.2%2B7/OpenJDK16U-jdk_x64_linux_hotspot_16.0.2_7.tar.gz";
    hash = "sha256-Mj1tdHSjWaKO/33dDfjmW9YVVKjtEu9C/ZNlNJ5XPCw=";
    lombok = lombok1_18_18;
    # https://github.com/rzwitserloot/lombok/issues/2681
    lombokArgs = map (pkg: "-J--add-opens=jdk.compiler/com.sun.tools.javac.${pkg}=ALL-UNNAMED") [
      "jvm" "util" "tree" "processing" "parser" "model" "main" "file" "comp" "code"
    ];
    supportedWarnings = openjdk15.meta.supportedWarnings ++ [ "missing-explicit-ctor" "synchronization" ];
  };
  openjdk17 = openjdk {
    release = 17;
    url = "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.9%2B9/OpenJDK17U-jdk_x64_linux_hotspot_17.0.9_9.tar.gz";
    hash = "sha256-exddvg1uPJwjtu2WRJsBgwjY/JSl7NnA34uLw3bDwYo=";
    lombok = lombok1_18_34;
    supportedWarnings = openjdk16.meta.supportedWarnings ++ [ "strictfp" ];
  };
  openjdk18 = openjdk {
    release = 18;
    url = "https://github.com/adoptium/temurin18-binaries/releases/download/jdk-18.0.2.1%2B1/OpenJDK18U-jdk_x64_linux_hotspot_18.0.2.1_1.tar.gz";
    hash = "sha256-fWvrqM/AqDR/J490FDURkalacH1GtlhumnhvJmmvD4s=";
    lombok = lombok1_18_34;
    supportedWarnings = openjdk17.meta.supportedWarnings;
  };
  openjdk19 = openjdk {
    release = 19;
    url = "https://github.com/adoptium/temurin19-binaries/releases/download/jdk-19.0.2%2B7/OpenJDK19U-jdk_x64_linux_hotspot_19.0.2_7.tar.gz";
    hash = "sha256-Ojuno/jDpZmeLJHqHcqENDWg0cQ3N70vaCKy8C/FIWU=";
    lombok = lombok1_18_34;
    supportedWarnings = openjdk18.meta.supportedWarnings;
  };
  openjdk20 = openjdk {
    release = 20;
    url = "https://github.com/adoptium/temurin20-binaries/releases/download/jdk-20.0.2%2B9/OpenJDK20U-jdk_x64_linux_hotspot_20.0.2_9.tar.gz";
    hash = "sha256-PZGELpwXKWesOXB2UjJJ0FqC6tUbAAaDj18DFa1SIiw=";
    lombok = lombok1_18_34;
    supportedWarnings = openjdk19.meta.supportedWarnings ++ [ "lossy-conversions" ];
  };
  openjdk21 = openjdk {
    release = 21;
    url = "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.4%2B7/OpenJDK21U-jdk_x64_linux_hotspot_21.0.4_7.tar.gz";
    hash = "sha256-UftNA6RCnDnTl9OgOneQdxWTF2FlUOTnFiTJhDCD57k=";
    lombok = lombok1_18_34;
    # also output-file-clash but that's not relevant to this website
    supportedWarnings = openjdk20.meta.supportedWarnings ++ [ "this-escape" ];
  };
  openjdk22 = openjdk {
    release = 22;
    url = "https://github.com/adoptium/temurin22-binaries/releases/download/jdk-22.0.2%2B9/OpenJDK22U-jdk_x64_linux_hotspot_22.0.2_9.tar.gz";
    hash = "sha256-Bc2TWdrLGhcw98VPV+D+1HlCpSkutWo6DuaxO4dFekM=";
    lombok = lombok1_18_34;
    supportedWarnings = openjdk21.meta.supportedWarnings ++ [ "incubating" "restricted" ];
  };

  # ecj runs as a jar on a host JDK. Lombok is loaded as an agent.
  ecj = { release, name, aliases ? [ ], jar, lombok, hostJdk, supportedWarnings }: {
    meta = {
      compiler = "ecj";
      release = toString release;
      hasLombok = true;
      inherit name aliases supportedWarnings;
    };
    runtime = hostedOn hostJdk {
      compiler = [ (java hostJdk) "-jar" "${jar}" "-cp" "${lombok}" ];
      compilerWithLombok = [ (java hostJdk) "-javaagent:${lombok}=ECJ" "-jar" "${jar}" "-cp" "${lombok}" ];
    };
  };

  ecj3_11 = ecj {
    release = 8;
    name = "Eclipse ECJ 3.11.1";
    aliases = [ "Eclipse ECJ 4.5.1" ]; # mistakenly called it this at first
    jar = maven "org.eclipse.jdt.core.compiler" "ecj" "4.5.1" "sha256-GfMT+xMZFHfnx+DxFC8Jb+8ZtyGXnvx/GM6oHSWecpQ=";
    lombok = lombok1_18_4;
    hostJdk = openjdk8;
    supportedWarnings = [
      "assertIdentifier" "boxing" "charConcat" "compareIdentical" "conditionAssign"
      "constructorName" "deadCode" "dep" "deprecation" "discouraged" "emptyBlock" "enumIdentifier"
      "enumSwitch" "enumSwitchPedantic" "fallthrough" "fieldHiding" "finalBound" "finally"
      "forbidden" "hashCode" "hiding" "includeAssertNull" "indirectStatic" "inheritNullAnnot"
      "intfAnnotation" "intfNonInherited" "intfRedundant" "invalidJavadoc" "invalidJavadocTag"
      "invalidJavadocTagDep" "invalidJavadocTagNotVisible" "invalidJavadocVisibility" "javadoc"
      "localHiding" "maskedCatchBlock" "missingJavadocTags" "missingJavadocTagsOverriding"
      "missingJavadocTagsMethod" "missingJavadocTagsVisibility" "missingJavadocComments"
      "missingJavadocCommentsOverriding" "missingJavadocCommentsVisibility" "nls" "noEffectAssign"
      "null" "nullAnnot" "nullAnnotConflict" "nullAnnotRedundant" "nullDereference"
      "nullUncheckedConversion" "over" "paramAssign" "pkgDefaultMethod" "raw" "resource"
      "semicolon" "serial" "specialParamHiding" "static" "staticReceiver" "super"
      "suppress" "switchDefault" "syncOverride" "syntacticAnalysis" "syntheticAccess" "tasks"
      "typeHiding" "unavoidableGenericProblems" "unchecked" "unnecessaryElse" "unqualifiedField"
      "unused" "unusedAllocation" "unusedArgument" "unusedExceptionParam" "unusedImport"
      "unusedLabel" "unusedLocal" "unusedParam" "unusedParamOverriding" "unusedParamImplementing"
      "unusedParamIncludeDoc" "unusedPrivate" "unusedThrown" "unusedThrownWhenOverriding"
      "unusedThrownIncludeDocComment" "unusedThrownExemptExceptionThrowable" "unusedTypeArgs"
      "uselessTypeCheck" "varargsCast" "warningToken"
    ];
  };
  ecj3_21 = ecj {
    release = 13;
    name = "Eclipse ECJ 3.21";
    jar = maven "org.eclipse.jdt" "ecj" "3.21.0" "sha256-kIIhH0h4J1AJPweCLRrkgejs4lBElXjzcjNNpibM3q0=";
    lombok = lombok1_18_4;
    hostJdk = openjdk14;
    supportedWarnings = ecj3_11.meta.supportedWarnings ++ [
      "module" "removal" "unlikelyCollectionMethodArgumentType" "unlikelyEqualsArgumentType"
    ];
  };
  ecj3_38 = ecj {
    release = 22;
    name = "Eclipse ECJ 3.38";
    jar = maven "org.eclipse.jdt" "ecj" "3.38.0" "sha256-l8VmsSAAnCA6L8iykfSprbwXHPHMtw8G9rThgowAzo4=";
    lombok = lombok1_18_34;
    hostJdk = openjdk21;
    supportedWarnings = ecj3_21.meta.supportedWarnings;
  };

  kotlinMeta = release: name: {
    compiler = "kotlinc";
    hasLombok = false;
    aliases = [ ];
    inherit release name;
  };

  # Kotlin compiler zip distribution, with kotlinx-coroutines on the classpath
  kotlinDistribution = { version, release ? version, name ? "Kotlin ${release}", hash, coroutines, hostJdk ? openjdk8 }:
    let
      dist = unpack "kotlin-${version}" (fetch {
        url = "https://github.com/JetBrains/kotlin/releases/download/v${version}/kotlin-compiler-${version}.zip";
        inherit hash;
      });
    in
    {
      meta = kotlinMeta release name;
      runtime = hostedOn hostJdk {
        compiler = [ "${dist}/bin/kotlinc" "-cp" "${coroutines}" ];
      };
    };

  # Old Kotlin compiler, as a single jar from maven central
  kotlinJar = { version, release ? version, hash }:
    let
      jar = maven "org.jetbrains.kotlin" "kotlin-compiler" version hash;
    in
    {
      meta = kotlinMeta release "Kotlin ${version}";
      runtime = hostedOn openjdk8 {
        compiler = [ (java openjdk8) "-jar" "${jar}" "-no-stdlib" "-cp" "${jar}" ];
      };
    };

  kotlin2_0_10 = kotlinDistribution {
    version = "2.0.10";
    hash = "sha256-iNfYutNirk4RSouWaMaIe4yF9I40CIPbDjF+R8jcL08=";
    hostJdk = openjdk21;
    coroutines = maven "org.jetbrains.kotlinx" "kotlinx-coroutines-core-jvm" "1.8.1" "sha256-89T13hw5G7zCDzs0Ncy6wBNSHna2kC19WWNewVwfeX4=";
  };
  kotlin1_6_10 = kotlinDistribution {
    version = "1.6.10";
    hash = "sha256-QyJnmW0Na0sXyo3g+HjkTUoJm36fFYepjtxNJ+dsIVo=";
    coroutines = maven "org.jetbrains.kotlinx" "kotlinx-coroutines-core" "1.6.0" "sha256-DAjh9A4AoPs19F/QZBYq0rWlqoibtGGXnPJtbuAV65Q=";
  };
  kotlin1_5_32 = kotlinDistribution {
    version = "1.5.32";
    hash = "sha256-LnKMQ+4L+Bnq4GYwpMu8KLou1bGaVe4K+W0sCra2wqU=";
    coroutines = maven "org.jetbrains.kotlinx" "kotlinx-coroutines-core" "1.5.2" "sha256-GWLrxs/7dkXG9fF1JGkNinKAhjRPEhnmPJygxNjzJ9c=";
  };
  kotlin1_4_30 = kotlinDistribution {
    version = "1.4.30";
    hash = "sha256-ewquncpeqJnvBd7cCm/dbjWUUeVv8N0zVEQ7MgizGAA=";
    coroutines = maven "org.jetbrains.kotlinx" "kotlinx-coroutines-core" "1.4.2" "sha256-TNJKBrKiUxENiv0lDp7sbG+q/qZGPXQIJHQ9Y352HxI=";
  };
  kotlin1_3_50 = kotlinDistribution {
    version = "1.3.50";
    hash = "sha256-aUJAkaa39S2T7ti7oqzpIbArET27cTiNcE+BgKa9xuw=";
    coroutines = maven "org.jetbrains.kotlinx" "kotlinx-coroutines-core" "1.3.2" "sha256-lQnozro9SRfsm723Srz6CY1Uq0uGqGszZV6pg3GTAx0=";
  };
  kotlin1_3_10 = kotlinDistribution {
    version = "1.3.10";
    hash = "sha256-ynnJMVHhTjT/Sc+1bsTA/oPhODFDsUaa+M3E9i+4xn0=";
    coroutines = maven "org.jetbrains.kotlinx" "kotlinx-coroutines-core" "1.0.1" "sha256-G7xRYBEYHDXCNVLzlYTZ4dzEbCw4SGyOcdVgYKZyKhI=";
  };
  kotlin1_2 = kotlinDistribution {
    version = "1.2.31";
    release = "1.2.0";
    name = "Kotlin 1.2.30";
    hash = "sha256-2pnGunelIilVKS6vv+hFmkaWlg745VhcvnHO5JzcgCY=";
    coroutines = maven "org.jetbrains.kotlinx" "kotlinx-coroutines-core" "0.30.2" "sha256-WNe+hT0RPs7ha1/07ITuWakJWpizMx4cLrYwjR3V+xY=";
  };

  kotlin1_1_4 = kotlinJar { version = "1.1.4-3"; release = "1.1.4"; hash = "sha256-kRq7U20tqMO2WWDhoelppu/yX+v97HiMF1vQlqUa5Ek="; };
  kotlin1_1_1 = kotlinJar { version = "1.1.1"; hash = "sha256-Hba3qTQ730T37eY4cB+7f7fw6ilRMh6Wd0IFc/bQrgk="; };
  kotlin1_0_6 = kotlinJar { version = "1.0.6"; hash = "sha256-3AlbKWyTA0A75fPtxPIZBmJUl/rR56moV7gOcCNKibU="; };
  kotlin1_0_5 = kotlinJar { version = "1.0.5"; hash = "sha256-yFlriatWgn/s+IlHGG2MX+Arb/MI7uSC1LrHJQWt1nE="; };
  kotlin1_0_4 = kotlinJar { version = "1.0.4"; hash = "sha256-qZrG9Fw2MEEQv4gRphEuSJuhmQRYFzGv/V5Zue4JARc="; };
  kotlin1_0_3 = kotlinJar { version = "1.0.3"; hash = "sha256-+xXC9RIQKFCLKBj4NSJ3F4dIQ1k7KYbAa7Rr5d8Edm4="; };
  kotlin1_0_2 = kotlinJar { version = "1.0.2"; hash = "sha256-YsY+VlzERQZTWdB62V0kwfzwffzj5XT0mXlLNwGd4hk="; };

  scala = { release, url, hash, supportedWarnings, hostJdk ? openjdk8 }:
    let
      dist = unpack "scala-${release}" (fetch { inherit url hash; });
    in
    {
      meta = {
        compiler = "scalac";
        name = "Scala ${release}";
        aliases = [ ];
        hasLombok = false;
        inherit release supportedWarnings;
      };
      runtime = hostedOn hostJdk {
        compiler = [ "${dist}/bin/scalac" ];
      };
    };
  scala2 = { release, hash, supportedWarnings }: scala {
    url = "https://downloads.lightbend.com/scala/${release}/scala-${release}.zip";
    inherit release hash supportedWarnings;
  };

  scala2_11_8 = scala2 {
    release = "2.11.8";
    hash = "sha256-QNxdkkRNaw4G91GttPEA+q+PhJvmgGySLIiQnYR07vo=";
    supportedWarnings = [
      "adapted-args" "nullary-unit" "inaccessible" "nullary-override" "infer-any" "missing-interpolator"
      "doc-detached" "private-shadow" "type-parameter-shadow" "poly-implicit-overload" "option-implicit"
      "delayedinit-select" "by-name-right-associative" "package-object-classes" "unsound-match" "stars-align"
    ];
  };
  scala2_12_0 = scala2 {
    release = "2.12.0";
    hash = "sha256-5uba37LAin2g5glcAjahCWngV0ZbwoOhmHpKFC66u0I=";
    supportedWarnings = scala2_11_8.meta.supportedWarnings ++ [ "constant" ];
  };
  scala2_12_5 = scala2 {
    release = "2.12.5";
    hash = "sha256-mNnkb46+NpZzmiAW4Ndjh3wCQrJlu3zOnuOsgo/iuxQ=";
    supportedWarnings = scala2_12_0.meta.supportedWarnings ++ [ "unused" ];
  };
  scala2_13 = scala2 {
    release = "2.13.1";
    hash = "sha256-iyxvDiahJLNR8Yq6GzlP7IBwwznMYUjerR5y2qT7JFs=";
    supportedWarnings =
      lib.subtractLists [ "by-name-right-associative" "unsound-match" ] scala2_12_5.meta.supportedWarnings
      ++ [ "nonlocal-return" "implicit-not-found" "serial" "valpattern" "eta-zero" "eta-sam" "deprecation" ];
  };
  scala3_4_2 = scala {
    release = "3.4.2";
    url = "https://github.com/scala/scala3/releases/download/3.4.2/scala3-3.4.2.zip";
    hash = "sha256-h1YtoYfSSi5TBDZ5FSAXUGIm7dreyBORJIREuL+LNhA=";
    # not sure about scala 3 warnings
    supportedWarnings = scala2_13.meta.supportedWarnings;
  };

  groups = [
    {
      label = "OpenJDK";
      sdks = [
        openjdk22 openjdk21 openjdk20 openjdk19 openjdk18 openjdk17 openjdk16 openjdk15 openjdk14 openjdk13
        openjdk12 openjdk11 openjdk10 openjdk9 openjdk8 openjdk7 openjdk6
      ];
    }
    {
      label = "Eclipse ECJ";
      sdks = [ ecj3_38 ecj3_21 ecj3_11 ];
    }
    {
      label = "Kotlin";
      sdks = [
        kotlin2_0_10 kotlin1_6_10 kotlin1_5_32 kotlin1_4_30 kotlin1_3_50 kotlin1_3_10 kotlin1_2
        kotlin1_1_4 kotlin1_1_1 kotlin1_0_6 kotlin1_0_5 kotlin1_0_4 kotlin1_0_3 kotlin1_0_2
      ];
    }
    {
      label = "Scala";
      sdks = [ scala3_4_2 scala2_13 scala2_12_5 scala2_12_0 scala2_11_8 ];
    }
  ];

  defaults = {
    JAVA = openjdk21;
    KOTLIN = kotlin2_0_10;
    SCALA = scala3_4_2;
  };

  allSdks = lib.concatMap (group: group.sdks) groups;

  # Manifest entry for one SDK: its runtime, plus the store paths the server may bind into the sandbox
  manifestEntry = sdk:
    let
      runtimeJson = pkgs.writeText "${lib.strings.sanitizeDerivationName sdk.meta.name}-runtime.json"
        (builtins.toJSON sdk.runtime);
      closure = pkgs.closureInfo { rootPaths = [ runtimeJson ]; };
    in
    pkgs.runCommand "${lib.strings.sanitizeDerivationName sdk.meta.name}-manifest.json"
      { nativeBuildInputs = [ pkgs.jq ]; }
      ''
        jq --arg name ${lib.escapeShellArg sdk.meta.name} --arg self ${runtimeJson} \
          --rawfile paths ${closure}/store-paths \
          '{ ($name): (. + { readable: ($paths | split("\n") | map(select(. != "" and . != $self))) }) }' \
          ${runtimeJson} > $out
      '';
in
{
  metadata = {
    groups = map (group: group // { sdks = map (sdk: sdk.meta) group.sdks; }) groups;
    defaults = lib.mapAttrs (_: sdk: sdk.meta.name) defaults;
  };

  manifest = pkgs.runCommand "javap-sdks" { nativeBuildInputs = [ pkgs.jq ]; } ''
    mkdir $out
    jq -s add ${lib.concatMapStringsSep " " manifestEntry allSdks} > $out/sdks.json
  '';
}
