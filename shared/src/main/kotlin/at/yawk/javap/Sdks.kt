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
    }

    data class OpenJdk(
            override val release: Int,

            override val name: String,
            val distribution: RemoteFile,
            val lombok: RemoteFile,
            val libPaths: Set<String>,
            override val supportedWarnings: Set<String>
    ) : Sdk(SdkLanguage.JAVA), Java

    data class Ecj(
            override val release: Int,

            override val name: String,
            val compilerJar: RemoteFile,
            val lombok: RemoteFile,
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
    private val lombok1_18_10 = RemoteFile("https://repo1.maven.org/maven2/org/projectlombok/lombok/1.18.10/lombok-1.18.10.jar")
    private val lombok1_18_4 = RemoteFile("https://repo1.maven.org/maven2/org/projectlombok/lombok/1.18.4/lombok-1.18.4.jar")

    private val openjdk6 = Sdk.OpenJdk(
            6,
            name = "OpenJDK 6u79",
            distribution = RemoteFile(
                    "http://cdn.azul.com/zulu/bin/zulu6.12.0.2-jdk6.0.79-linux_x64.tar.gz",
                    md5 = "a54fb082c89de3909df5b69cd50a2155"
            ),
            libPaths = setOf("lib/amd64", "lib/amd64/jli"),
            lombok = lombok1_18_4,
            supportedWarnings = setOf(
                    "cast", "deprecation", "divzero", "empty", "unchecked", "fallthrough", "path", "serial",
                    "finally", "overrides")
    )
    private val openjdk7 = Sdk.OpenJdk(
            7,
            name = "OpenJDK 7u101",
            distribution = RemoteFile(
                    "http://cdn.azul.com/zulu/bin/zulu7.14.0.5-jdk7.0.101-linux_x64.tar.gz",
                    md5 = "6fd6af8bc9a696116b7eeff8d28b0e98"
            ),
            libPaths = setOf("lib/amd64", "lib/amd64/jli"),
            lombok = lombok1_18_4,
            supportedWarnings = openjdk6.supportedWarnings + setOf(
                    "classfile", "dep-ann", "options", "processing", "rawtypes", "static", "try", "varargs")
    )
    private val openjdk8 = Sdk.OpenJdk(
            8,
            name = "OpenJDK 8u92",
            distribution = RemoteFile(
                    "http://cdn.azul.com/zulu/bin/zulu8.15.0.1-jdk8.0.92-linux_x64.tar.gz",
                    md5 = "509fef886f7c6992d0f6f133c4928ec9"
            ),
            libPaths = setOf("lib/amd64", "lib/amd64/jli"),
            lombok = lombok1_18_4,
            supportedWarnings = openjdk7.supportedWarnings + setOf("auxiliaryclass", "overloads")
    )
    private val openjdk9 = Sdk.OpenJdk(
            9,
            name = "OpenJDK 9.0.0",
            distribution = RemoteFile(
                    "https://github.com/AdoptOpenJDK/openjdk9-binaries/releases/download/jdk-9.0.4%2B11/OpenJDK9U-jdk_x64_linux_hotspot_9.0.4_11.tar.gz",
                    sha256 = "aa4fc8bda11741facaf3b8537258a4497c6e0046b9f4931e31f713d183b951f1"
            ),
            libPaths = setOf("lib"),
            lombok = lombok1_18_4,
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
            lombok = lombok1_18_4,
            supportedWarnings = openjdk9.supportedWarnings
    )
    private val openjdk11 = Sdk.OpenJdk(
            11,
            name = "OpenJDK 11",
            distribution = RemoteFile(
                    "https://github.com/AdoptOpenJDK/openjdk11-binaries/releases/download/jdk-11.0.1%2B13/OpenJDK11U-jdk_x64_linux_hotspot_11.0.1_13.tar.gz",
                    sha256 = "22bd2f1a2e0cb6e4075967bfeda4a960b0325879305aa739a0ba2d6e5cd4c3e2"
            ),
            libPaths = setOf("lib"),
            lombok = lombok1_18_4,
            supportedWarnings = openjdk10.supportedWarnings + "preview"
    )
    private val openjdk12 = Sdk.OpenJdk(
            12,
            name = "OpenJDK 12.0.2",
            distribution = RemoteFile(
                    "https://github.com/AdoptOpenJDK/openjdk12-binaries/releases/download/jdk-12.0.2%2B10/OpenJDK12U-jdk_x64_linux_hotspot_12.0.2_10.tar.gz",
                    sha256 = "1202f536984c28d68681d51207a84b6c76e5998579132d3fe1b8085aa6a5f21e"
            ),
            libPaths = setOf("lib"),
            lombok = lombok1_18_10,
            supportedWarnings = openjdk11.supportedWarnings + "text-blocks"
    )
    private val openjdk13 = Sdk.OpenJdk(
            13,
            name = "OpenJDK 13",
            distribution = RemoteFile(
                    "https://github.com/AdoptOpenJDK/openjdk13-binaries/releases/download/jdk-13%2B33/OpenJDK13U-jdk_x64_linux_hotspot_13_33.tar.gz",
                    sha256 = "e562caeffa89c834a69a44242d802eae3523875e427f07c05b1902c152638368"
            ),
            libPaths = setOf("lib"),
            lombok = lombok1_18_10,
            supportedWarnings = openjdk12.supportedWarnings
    )
    private val openjdk14 = Sdk.OpenJdk(
            14,
            name = "OpenJDK 14",
            distribution = RemoteFile(
                    "https://github.com/AdoptOpenJDK/openjdk14-binaries/releases/download/jdk-14%2B36/OpenJDK14U-jdk_x64_linux_hotspot_14_36.tar.gz",
                    sha256 = "6c06853332585ab58834d9e8a02774b388e6e062ef6c4084b4f058c67f2e81b5"
            ),
            libPaths = setOf("lib"),
            lombok = lombok1_18_10,
            supportedWarnings = openjdk13.supportedWarnings
    )
    private val openjdk = listOf(
            openjdk14, openjdk13, openjdk12, openjdk11, openjdk10, openjdk9, openjdk8, openjdk7, openjdk6)
    val defaultJava = openjdk14

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
            release = 9,
            name = "Eclipse ECJ 3.21",
            compilerJar = RemoteFile("https://repo1.maven.org/maven2/org/eclipse/jdt/ecj/3.21.0/ecj-3.21.0.jar"),
            lombok = lombok1_18_10,
            hostJdk = openjdk8,
            supportedWarnings = ecj3_11.supportedWarnings +
                    setOf("module", "removal", "unlikelyCollectionMethodArgumentType", "unlikelyEqualsArgumentType")
    )
    private val ecj = listOf(ecj3_21, ecj3_11)

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
            kotlin1_3_50, kotlin1_3_10, kotlin1_2, kotlin1_1_4, kotlin1_1_1, kotlin1_0_6, kotlin1_0_5, kotlin1_0_4,
            kotlin1_0_3, kotlin1_0_2)
    private val defaultKotlin = kotlin1_3_50

    private fun scalaSdk(release: KotlinVersion, warnings: Set<String>) = Sdk.Scala(
            release = release,
            name = "Scala $release",
            sdk = RemoteFile("https://downloads.lightbend.com/scala/$release/scala-$release.zip"),
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

    private val scala = listOf(scala2_13, scala2_12_5, scala2_12_0, scala2_11_8)
    private val defaultScala = scala2_13

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