package at.yawk.javap

data class RemoteFile(
        val url: String,
        val md5: String? = null,
        val sha256: String? = null,
        val sha512: String? = null
)

sealed class Sdk(
        val language: SdkLanguage
) {
    abstract val name: String

    /**
     * Previous SDK names that should now be treated as this SDK
     */
    open val aliases: Set<String>
        get() = emptySet()

    interface HasLint {
        val supportedWarnings: Set<String>
    }

    interface Java : HasLint {
        val release: Int
        val lombok: RemoteFile?
    }

    data class OpenJdk(
            override val release: Int,

            override val name: String,
            val distribution: RemoteFile,
            override val lombok: RemoteFile?,
            val libPaths: Set<String>,
            override val supportedWarnings: Set<String>,

            override val aliases: Set<String> = emptySet()
    ) : Sdk(SdkLanguage.JAVA), Java

    data class Ecj(
            override val release: Int,

            override val name: String,
            val compilerJar: RemoteFile,
            override val lombok: RemoteFile,
            val hostJdk: OpenJdk,
            override val supportedWarnings: Set<String>,

            override val aliases: Set<String> = emptySet()
    ) : Sdk(SdkLanguage.JAVA), Java

    interface Kotlin {
        val release: KotlinVersion
    }

    data class KotlinJar(
            override val release: KotlinVersion,
            override val name: String,
            val compilerJar: RemoteFile,
            val hostJdk: OpenJdk
    ) : Sdk(SdkLanguage.KOTLIN), Kotlin

    data class KotlinDistribution(
            override val release: KotlinVersion,
            override val name: String,
            val distribution: RemoteFile,
            val hostJdk: OpenJdk,
            val coroutines: RemoteFile
    ) : Sdk(SdkLanguage.KOTLIN), Kotlin

    data class Scala(
            val release: KotlinVersion,
            override val name: String,
            val sdk: RemoteFile,
            val hostJdk: OpenJdk,
            override val supportedWarnings: Set<String>
    ) : Sdk(SdkLanguage.SCALA), HasLint
}

object Sdks {
    private val lombok1_18_34 = RemoteFile("https://repo1.maven.org/maven2/org/projectlombok/lombok/1.18.34/lombok-1.18.34.jar")
    private val lombok1_18_24 = RemoteFile("https://repo1.maven.org/maven2/org/projectlombok/lombok/1.18.24/lombok-1.18.24.jar")
    private val lombok1_18_18 = RemoteFile("https://repo1.maven.org/maven2/org/projectlombok/lombok/1.18.18/lombok-1.18.18.jar")
    private val lombok1_18_4 = RemoteFile("https://repo1.maven.org/maven2/org/projectlombok/lombok/1.18.4/lombok-1.18.4.jar")

    private val openjdk6 = Sdk.OpenJdk(
            6,
            name = "OpenJDK 6",
            aliases = setOf("OpenJDK 6u79"),
            distribution = RemoteFile(
                    "https://cdn.azul.com/zulu/bin/zulu6.22.0.3-jdk6.0.119-linux_x64.tar.gz",
                    sha256 = "dd4765b45d9f1fc319a6e5be0bbc79371582daa8fd77872d51072f16deca2625"
            ),
            libPaths = setOf("lib/amd64", "lib/amd64/jli"),
            lombok = lombok1_18_4,
            supportedWarnings = setOf(
                    "cast", "deprecation", "divzero", "empty", "unchecked", "fallthrough", "path", "serial",
                    "finally", "overrides")
    )
    private val openjdk7 = Sdk.OpenJdk(
            7,
            name = "OpenJDK 7",
            aliases = setOf("OpenJDK 7u101"),
            distribution = RemoteFile(
                    "https://cdn.azul.com/zulu/bin/zulu7.56.0.11-ca-jdk7.0.352-linux_x64.tar.gz",
                    sha256 = "8a7387c1ed151474301b6553c6046f865dc6c1e1890bcf106acc2780c55727c8"
            ),
            libPaths = setOf("lib/amd64", "lib/amd64/jli"),
            lombok = lombok1_18_18,
            supportedWarnings = openjdk6.supportedWarnings + setOf(
                    "classfile", "dep-ann", "options", "processing", "rawtypes", "static", "try", "varargs")
    )
    private val openjdk8 = Sdk.OpenJdk(
            8,
            name = "OpenJDK 8",
            aliases = setOf("OpenJDK 8u92"),
            distribution = RemoteFile(
                    "https://github.com/adoptium/temurin8-binaries/releases/download/jdk8u422-b05/OpenJDK8U-jdk_x64_linux_hotspot_8u422b05.tar.gz",
                    sha256 = "4c6056f6167fae73ace7c3080b78940be5c87d54f5b08894b3517eed1cbb2c06"
            ),
            libPaths = setOf("lib/amd64", "lib/amd64/jli"),
            lombok = lombok1_18_18,
            supportedWarnings = openjdk7.supportedWarnings + setOf("auxiliaryclass", "overloads")
    )
    private val openjdk9 = Sdk.OpenJdk(
            9,
            name = "OpenJDK 9",
            aliases = setOf("OpenJDK 9.0.0"),
            distribution = RemoteFile(
                    "https://github.com/AdoptOpenJDK/openjdk9-binaries/releases/download/jdk-9%2B181/OpenJDK9U-jdk_x64_linux_hotspot_9_181.tar.gz",
                    sha256 = "ebe1eaad73584f70ab4e587ab9367fb15af820745979e68350fe80fadf615df8"
            ),
            libPaths = setOf("lib"),
            lombok = lombok1_18_18,
            supportedWarnings = openjdk8.supportedWarnings + setOf(
                    "exports", "module", "opens", "removal", "requires-automatic", "requires-transitive-automatic")
    )
    private val openjdk10 = Sdk.OpenJdk(
            10,
            name = "OpenJDK 10",
            distribution = RemoteFile(
                    "https://github.com/AdoptOpenJDK/openjdk10-binaries/releases/download/jdk-10.0.2%2B13.1/OpenJDK10U-jdk_x64_linux_hotspot_10.0.2_13.tar.gz",
                    sha256 = "3998c36c7feb4bb7a565b3d33609ec67acd40f1ae5addf103378f2ef32ab377f"
            ),
            libPaths = setOf("lib"),
            lombok = lombok1_18_18,
            supportedWarnings = openjdk9.supportedWarnings
    )
    private val openjdk11 = Sdk.OpenJdk(
            11,
            name = "OpenJDK 11",
            distribution = RemoteFile(
                    "https://github.com/adoptium/temurin11-binaries/releases/download/jdk-11.0.24%2B8/OpenJDK11U-jdk_x64_linux_hotspot_11.0.24_8.tar.gz",
                    sha256 = "0e71a01563a5c7b9988a168b0c4ce720a6dff966b3c27bb29d1ded461ff71d0e"
            ),
            libPaths = setOf("lib"),
            lombok = lombok1_18_18,
            supportedWarnings = openjdk10.supportedWarnings + "preview"
    )
    private val openjdk12 = Sdk.OpenJdk(
            12,
            name = "OpenJDK 12",
            aliases = setOf("OpenJDK 12.0.2"),
            distribution = RemoteFile(
                    "https://github.com/AdoptOpenJDK/openjdk12-binaries/releases/download/jdk-12.0.2%2B10/OpenJDK12U-jdk_x64_linux_hotspot_12.0.2_10.tar.gz",
                    sha256 = "1202f536984c28d68681d51207a84b6c76e5998579132d3fe1b8085aa6a5f21e"
            ),
            libPaths = setOf("lib"),
            lombok = lombok1_18_18,
            supportedWarnings = openjdk11.supportedWarnings + "text-blocks"
    )
    private val openjdk13 = Sdk.OpenJdk(
            13,
            name = "OpenJDK 13",
            distribution = RemoteFile(
                    "https://github.com/AdoptOpenJDK/openjdk13-binaries/releases/download/jdk-13.0.2%2B8/OpenJDK13U-jdk_x64_linux_hotspot_13.0.2_8.tar.gz",
                    sha256 = "9ccc063569f19899fd08e41466f8c4cd4e05058abdb5178fa374cb365dcf5998"
            ),
            libPaths = setOf("lib"),
            lombok = lombok1_18_18,
            supportedWarnings = openjdk12.supportedWarnings
    )
    private val openjdk14 = Sdk.OpenJdk(
            14,
            name = "OpenJDK 14",
            distribution = RemoteFile(
                    "https://github.com/AdoptOpenJDK/openjdk14-binaries/releases/download/jdk-14.0.2%2B12/OpenJDK14U-jdk_x64_linux_hotspot_14.0.2_12.tar.gz",
                    sha256 = "7d5ee7e06909b8a99c0d029f512f67b092597aa5b0e78c109bd59405bbfa74fe"
            ),
            libPaths = setOf("lib"),
            lombok = lombok1_18_18,
            supportedWarnings = openjdk13.supportedWarnings
    )
    private val openjdk15 = Sdk.OpenJdk(
            15,
            name = "OpenJDK 15",
            distribution = RemoteFile(
                    "https://github.com/AdoptOpenJDK/openjdk15-binaries/releases/download/jdk-15.0.2%2B7/OpenJDK15U-jdk_x64_linux_hotspot_15.0.2_7.tar.gz",
                    sha256 = "94f20ca8ea97773571492e622563883b8869438a015d02df6028180dd9acc24d"
            ),
            libPaths = setOf("lib"),
            lombok = lombok1_18_18,
            supportedWarnings = openjdk14.supportedWarnings
    )
    private val openjdk16 = Sdk.OpenJdk(
            16,
            name = "OpenJDK 16",
            distribution = RemoteFile(
                    "https://github.com/adoptium/temurin16-binaries/releases/download/jdk-16.0.2%2B7/OpenJDK16U-jdk_x64_linux_hotspot_16.0.2_7.tar.gz",
                    sha256 = "323d6d7474a359a28eff7ddd0df8e65bd61554a8ed12ef42fd9365349e573c2c"
            ),
            libPaths = setOf("lib"),
            lombok = lombok1_18_18,
            supportedWarnings = openjdk15.supportedWarnings + setOf("missing-explicit-ctor", "synchronization")
    )
    private val openjdk17 = Sdk.OpenJdk(
            17,
            name = "OpenJDK 17",
            distribution = RemoteFile(
                    "https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.9%2B9/OpenJDK17U-jdk_x64_linux_hotspot_17.0.9_9.tar.gz",
                    sha256 = "7b175dbe0d6e3c9c23b6ed96449b018308d8fc94a5ecd9c0df8b8bc376c3c18a"
            ),
            libPaths = setOf("lib"),
            lombok = lombok1_18_34,
            supportedWarnings = openjdk16.supportedWarnings + "strictfp"
    )
    private val openjdk18 = Sdk.OpenJdk(
            18,
            name = "OpenJDK 18",
            distribution = RemoteFile(
                    "https://github.com/adoptium/temurin18-binaries/releases/download/jdk-18.0.2.1%2B1/OpenJDK18U-jdk_x64_linux_hotspot_18.0.2.1_1.tar.gz",
                    sha256 = "7d6beba8cfc0a8347f278f7414351191a95a707d46b6586e9a786f2669af0f8b"
            ),
            libPaths = setOf("lib"),
            lombok = lombok1_18_34,
            supportedWarnings = openjdk17.supportedWarnings
    )
    private val openjdk19 = Sdk.OpenJdk(
            19,
            name = "OpenJDK 19",
            distribution = RemoteFile(
                    "https://github.com/adoptium/temurin19-binaries/releases/download/jdk-19.0.2%2B7/OpenJDK19U-jdk_x64_linux_hotspot_19.0.2_7.tar.gz",
                    sha256 = "3a3ba7a3f8c3a5999e2c91ea1dca843435a0d1c43737bd2f6822b2f02fc52165"
            ),
            libPaths = setOf("lib"),
            lombok = lombok1_18_34,
            supportedWarnings = openjdk18.supportedWarnings
    )
    private val openjdk20 = Sdk.OpenJdk(
            20,
            name = "OpenJDK 20",
            distribution = RemoteFile(
                    "https://github.com/adoptium/temurin20-binaries/releases/download/jdk-20.0.2%2B9/OpenJDK20U-jdk_x64_linux_hotspot_20.0.2_9.tar.gz",
                    sha256 = "3d91842e9c172967ac397076523249d05a82ead51b0006838f5f0315ad52222c"
            ),
            libPaths = setOf("lib"),
            lombok = lombok1_18_34,
            supportedWarnings = openjdk19.supportedWarnings + "lossy-conversions"
    )
    private val openjdk21 = Sdk.OpenJdk(
            21,
            name = "OpenJDK 21",
            distribution = RemoteFile(
                    "https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.4%2B7/OpenJDK21U-jdk_x64_linux_hotspot_21.0.4_7.tar.gz",
                    sha256 = "51fb4d03a4429c39d397d3a03a779077159317616550e4e71624c9843083e7b9"
            ),
            libPaths = setOf("lib"),
            lombok = lombok1_18_34,
            // also output-file-clash but that's not relevant to this website
            supportedWarnings = openjdk20.supportedWarnings + "this-escape"
    )
    private val openjdk22 = Sdk.OpenJdk(
            22,
            name = "OpenJDK 22",
            distribution = RemoteFile(
                    "https://github.com/adoptium/temurin22-binaries/releases/download/jdk-22.0.2%2B9/OpenJDK22U-jdk_x64_linux_hotspot_22.0.2_9.tar.gz",
                    sha256 = "05cd9359dacb1a1730f7c54f57e0fed47942a5292eb56a3a0ee6b13b87457a43"
            ),
            libPaths = setOf("lib"),
            lombok = lombok1_18_34,
            supportedWarnings = openjdk21.supportedWarnings + setOf("incubating", "restricted")
    )
    private val openjdk = listOf(
            openjdk22, openjdk21, openjdk20, openjdk19, openjdk18, openjdk17, openjdk16, openjdk15, openjdk14, openjdk13, openjdk12, openjdk11, openjdk10, openjdk9, openjdk8, openjdk7, openjdk6)
    val defaultJava = openjdk21

    private val ecj3_11 = Sdk.Ecj(
            release = 8,
            name = "Eclipse ECJ 3.11.1",
            aliases = setOf("Eclipse ECJ 4.5.1"), // mistakenly called it this at first
            compilerJar = RemoteFile("https://repo1.maven.org/maven2/org/eclipse/jdt/core/compiler/ecj/4.5.1/ecj-4.5.1.jar"),
            lombok = lombok1_18_4,
            hostJdk = openjdk8,
            supportedWarnings = setOf(
                    "assertIdentifier", "boxing", "charConcat", "compareIdentical", "conditionAssign",
                    "constructorName", "deadCode", "dep", "deprecation", "discouraged", "emptyBlock", "enumIdentifier",
                    "enumSwitch", "enumSwitchPedantic", "fallthrough", "fieldHiding", "finalBound", "finally",
                    "forbidden", "hashCode", "hiding", "includeAssertNull", "indirectStatic", "inheritNullAnnot",
                    "intfAnnotation", "intfNonInherited", "intfRedundant", "invalidJavadoc", "invalidJavadocTag",
                    "invalidJavadocTagDep", "invalidJavadocTagNotVisible", "invalidJavadocVisibility", "javadoc",
                    "localHiding", "maskedCatchBlock", "missingJavadocTags", "missingJavadocTagsOverriding",
                    "missingJavadocTagsMethod", "missingJavadocTagsVisibility", "missingJavadocComments",
                    "missingJavadocCommentsOverriding", "missingJavadocCommentsVisibility", "nls", "noEffectAssign",
                    "null", "nullAnnot", "nullAnnotConflict", "nullAnnotRedundant", "nullDereference",
                    "nullUncheckedConversion", "over", "paramAssign", "pkgDefaultMethod", "raw", "resource",
                    "semicolon", "serial", "specialParamHiding", "static", "static", "staticReceiver", "super",
                    "suppress", "switchDefault", "syncOverride", "syntacticAnalysis", "syntheticAccess", "tasks",
                    "typeHiding", "unavoidableGenericProblems", "unchecked", "unnecessaryElse", "unqualifiedField",
                    "unused", "unusedAllocation", "unusedArgument", "unusedExceptionParam", "unusedImport",
                    "unusedLabel", "unusedLocal", "unusedParam", "unusedParamOverriding", "unusedParamImplementing",
                    "unusedParamIncludeDoc", "unusedPrivate", "unusedThrown", "unusedThrownWhenOverriding",
                    "unusedThrownIncludeDocComment", "unusedThrownExemptExceptionThrowable", "unusedTypeArgs",
                    "uselessTypeCheck", "varargsCast", "warningToken"
            )
    )
    private val ecj3_21 = Sdk.Ecj(
            release = 13,
            name = "Eclipse ECJ 3.21",
            compilerJar = RemoteFile("https://repo1.maven.org/maven2/org/eclipse/jdt/ecj/3.21.0/ecj-3.21.0.jar"),
            lombok = lombok1_18_4,
            hostJdk = openjdk14,
            supportedWarnings = ecj3_11.supportedWarnings +
                    setOf("module", "removal", "unlikelyCollectionMethodArgumentType", "unlikelyEqualsArgumentType")
    )
    private val ecj3_38 = Sdk.Ecj(
            release = 22,
            name = "Eclipse ECJ 3.38",
            compilerJar = RemoteFile("https://repo1.maven.org/maven2/org/eclipse/jdt/ecj/3.38.0/ecj-3.38.0.jar"),
            lombok = lombok1_18_34,
            hostJdk = openjdk21,
            supportedWarnings = ecj3_21.supportedWarnings
    )
    private val ecj = listOf(ecj3_38, ecj3_21, ecj3_11)

    private val kotlin2_0_10 = Sdk.KotlinDistribution(
            KotlinVersion(2, 0, 10),
            name = "Kotlin 2.0.10",
            distribution = RemoteFile("https://github.com/JetBrains/kotlin/releases/download/v2.0.10/kotlin-compiler-2.0.10.zip"),
            hostJdk = openjdk21,
            coroutines = RemoteFile("https://repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-core-jvm/1.8.1/kotlinx-coroutines-core-jvm-1.8.1.jar")
    )
    private val kotlin1_6_10 = Sdk.KotlinDistribution(
            KotlinVersion(1, 6, 10),
            name = "Kotlin 1.6.10",
            distribution = RemoteFile("https://github.com/JetBrains/kotlin/releases/download/v1.6.10/kotlin-compiler-1.6.10.zip"),
            hostJdk = openjdk8,
            coroutines = RemoteFile("https://repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-core/1.6.0/kotlinx-coroutines-core-1.6.0.jar")
    )
    private val kotlin1_5_32 = Sdk.KotlinDistribution(
            KotlinVersion(1, 5, 32),
            name = "Kotlin 1.5.32",
            distribution = RemoteFile("https://github.com/JetBrains/kotlin/releases/download/v1.5.32/kotlin-compiler-1.5.32.zip"),
            hostJdk = openjdk8,
            coroutines = RemoteFile("https://repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-core/1.5.2/kotlinx-coroutines-core-1.5.2.jar")
    )
    private val kotlin1_4_30 = Sdk.KotlinDistribution(
            KotlinVersion(1, 4, 30),
            name = "Kotlin 1.4.30",
            distribution = RemoteFile("https://github.com/JetBrains/kotlin/releases/download/v1.4.30/kotlin-compiler-1.4.30.zip"),
            hostJdk = openjdk8,
            coroutines = RemoteFile("https://repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-core/1.4.2/kotlinx-coroutines-core-1.4.2.jar")
    )
    private val kotlin1_3_50 = Sdk.KotlinDistribution(
            KotlinVersion(1, 3, 50),
            name = "Kotlin 1.3.50",
            distribution = RemoteFile("https://github.com/JetBrains/kotlin/releases/download/v1.3.50/kotlin-compiler-1.3.50.zip"),
            hostJdk = openjdk8,
            coroutines = RemoteFile("https://repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-core/1.3.2/kotlinx-coroutines-core-1.3.2.jar")
    )
    private val kotlin1_3_10 = Sdk.KotlinDistribution(
            KotlinVersion(1, 3, 10),
            name = "Kotlin 1.3.10",
            distribution = RemoteFile("https://github.com/JetBrains/kotlin/releases/download/v1.3.10/kotlin-compiler-1.3.10.zip"),
            hostJdk = openjdk8,
            coroutines = RemoteFile("https://repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-core/1.0.1/kotlinx-coroutines-core-1.0.1.jar")
    )
    private val kotlin1_2 = Sdk.KotlinDistribution(
            KotlinVersion(1, 2),
            name = "Kotlin 1.2.30",
            distribution = RemoteFile("https://github.com/JetBrains/kotlin/releases/download/v1.2.31/kotlin-compiler-1.2.31.zip"),
            hostJdk = openjdk8,
            coroutines = RemoteFile("https://repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-core/0.30.2/kotlinx-coroutines-core-0.30.2.jar")
    )

    private fun kotlinJar(version: KotlinVersion, name: String = version.toString()) = Sdk.KotlinJar(
            version,
            name = "Kotlin $name",
            compilerJar = RemoteFile("https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-compiler/$name/kotlin-compiler-$name.jar"),
            hostJdk = openjdk8
    )

    private val kotlin1_1_4 = kotlinJar(KotlinVersion(1, 1, 4), "1.1.4-3")
    private val kotlin1_1_1 = kotlinJar(KotlinVersion(1, 1, 1))
    private val kotlin1_0_6 = kotlinJar(KotlinVersion(1, 0, 6))
    private val kotlin1_0_5 = kotlinJar(KotlinVersion(1, 0, 5))
    private val kotlin1_0_4 = kotlinJar(KotlinVersion(1, 0, 4))
    private val kotlin1_0_3 = kotlinJar(KotlinVersion(1, 0, 3))
    private val kotlin1_0_2 = kotlinJar(KotlinVersion(1, 0, 2))

    private val kotlin = listOf<Sdk>(
        kotlin2_0_10,
        kotlin1_6_10, kotlin1_5_32, kotlin1_4_30, kotlin1_3_50, kotlin1_3_10, kotlin1_2, kotlin1_1_4, kotlin1_1_1, kotlin1_0_6, kotlin1_0_5,
        kotlin1_0_4, kotlin1_0_3, kotlin1_0_2)
    private val defaultKotlin = kotlin2_0_10

    private fun scalaSdk(release: KotlinVersion, warnings: Set<String>) = Sdk.Scala(
            release = release,
            name = "Scala $release",
            sdk = RemoteFile("https://downloads.lightbend.com/scala/$release/scala-$release.zip"),
            hostJdk = openjdk8,
            supportedWarnings = warnings
    )
    private fun scala3Sdk(release: KotlinVersion, warnings: Set<String>) = Sdk.Scala(
            release = release,
            name = "Scala $release",
            sdk = RemoteFile("https://github.com/scala/scala3/releases/download/$release/scala3-$release.zip"),
            hostJdk = openjdk8,
            supportedWarnings = warnings
    )

    private val scala2_11_8 = scalaSdk(KotlinVersion(2, 11, 8), warnings = setOf(
            "adapted-args", "nullary-unit", "inaccessible", "nullary-override", "infer-any", "missing-interpolator",
            "doc-detached", "private-shadow", "type-parameter-shadow", "poly-implicit-overload", "option-implicit",
            "delayedinit-select", "by-name-right-associative", "package-object-classes", "unsound-match", "stars-align"
    ))
    private val scala2_12_0 = scalaSdk(KotlinVersion(2, 12, 0), warnings = scala2_11_8.supportedWarnings + "constant")
    private val scala2_12_5 = scalaSdk(KotlinVersion(2, 12, 5), warnings = scala2_12_0.supportedWarnings + "unused")
    private val scala2_13 = scalaSdk(KotlinVersion(2, 13, 1), warnings = scala2_12_5.supportedWarnings -
            setOf("by-name-right-associative", "unsound-match") +
            setOf("nonlocal-return", "implicit-not-found", "serial", "valpattern", "eta-zero", "eta-sam",
                    "deprecation"))
    // not sure about scala 3 warnings
    private val scala3_4_2 = scala3Sdk(KotlinVersion(3, 4, 2), warnings = scala2_13.supportedWarnings)

    private val scala = listOf(scala3_4_2, scala2_13, scala2_12_5, scala2_12_0, scala2_11_8)
    private val defaultScala = scala3_4_2

    val sdkByLabel = mapOf(
            "OpenJDK" to openjdk,
            "Eclipse ECJ" to ecj,
            "Kotlin" to kotlin,
            "Scala" to scala
    )
    val defaultSdks = mapOf(
            SdkLanguage.JAVA to defaultJava,
            SdkLanguage.KOTLIN to defaultKotlin,
            SdkLanguage.SCALA to defaultScala
    )

    val sdksByName: Map<String, Sdk>

    init {
        val sdksByName = mutableMapOf<String, Sdk>()
        for (sdk in openjdk as List<Sdk> + ecj + kotlin + scala) {
            sdksByName[sdk.name] = sdk
            for (alias in sdk.aliases) {
                sdksByName[alias] = sdk
            }
        }
        this.sdksByName = sdksByName
    }

    val allSupportedWarnings: Set<String> = (openjdk as List<Sdk.HasLint> + ecj + scala)
            .flatMapTo(mutableSetOf()) { it.supportedWarnings }
}