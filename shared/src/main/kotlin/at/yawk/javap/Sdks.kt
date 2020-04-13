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

    data class OpenJdk(
            override val name: String,
            val distribution: RemoteFile,
            val lombok: RemoteFile,
            val libPaths: Set<String>
    ) : Sdk(SdkLanguage.JAVA)

    data class Ecj(
            override val name: String,
            val compilerJar: RemoteFile,
            val lombok: RemoteFile,
            val hostJdk: OpenJdk
    ) : Sdk(SdkLanguage.JAVA)

    data class KotlinJar(
            override val name: String,
            val compilerJar: RemoteFile,
            val hostJdk: OpenJdk
    ) : Sdk(SdkLanguage.KOTLIN)

    data class KotlinDistribution(
            override val name: String,
            val distribution: RemoteFile,
            val hostJdk: OpenJdk,
            val coroutines: RemoteFile
    ) : Sdk(SdkLanguage.KOTLIN)

    data class Scala(
            override val name: String,
            val sdk: RemoteFile,
            val hostJdk: OpenJdk
    ) : Sdk(SdkLanguage.SCALA)
}

object Sdks {
    private val lombok1_18_10 = RemoteFile("https://repo1.maven.org/maven2/org/projectlombok/lombok/1.18.10/lombok-1.18.10.jar")
    private val lombok1_18_4 = RemoteFile("https://repo1.maven.org/maven2/org/projectlombok/lombok/1.18.4/lombok-1.18.4.jar")

    private val openjdk13 = Sdk.OpenJdk(
            name = "OpenJDK 13",
            distribution = RemoteFile(
                    "https://github.com/AdoptOpenJDK/openjdk13-binaries/releases/download/jdk-13%2B33/OpenJDK13U-jdk_x64_linux_hotspot_13_33.tar.gz",
                    sha256 = "e562caeffa89c834a69a44242d802eae3523875e427f07c05b1902c152638368"
            ),
            libPaths = setOf("lib"),
            lombok = lombok1_18_10
    )
    private val openjdk12 = Sdk.OpenJdk(
            name = "OpenJDK 12.0.2",
            distribution = RemoteFile(
                    "https://github.com/AdoptOpenJDK/openjdk12-binaries/releases/download/jdk-12.0.2%2B10/OpenJDK12U-jdk_x64_linux_hotspot_12.0.2_10.tar.gz",
                    sha256 = "1202f536984c28d68681d51207a84b6c76e5998579132d3fe1b8085aa6a5f21e"
            ),
            libPaths = setOf("lib"),
            lombok = lombok1_18_10
    )
    private val openjdk11 = Sdk.OpenJdk(
            name = "OpenJDK 11",
            distribution = RemoteFile(
                    "https://github.com/AdoptOpenJDK/openjdk11-binaries/releases/download/jdk-11.0.1%2B13/OpenJDK11U-jdk_x64_linux_hotspot_11.0.1_13.tar.gz",
                    sha256 = "22bd2f1a2e0cb6e4075967bfeda4a960b0325879305aa739a0ba2d6e5cd4c3e2"
            ),
            libPaths = setOf("lib"),
            lombok = lombok1_18_4
    )
    private val openjdk10 = Sdk.OpenJdk(
            name = "OpenJDK 10",
            distribution = RemoteFile(
                    "https://github.com/AdoptOpenJDK/openjdk10-binaries/releases/download/jdk-10.0.2%2B13.1/OpenJDK10U-jdk_x64_linux_hotspot_10.0.2_13.tar.gz",
                    sha256 = "3998c36c7feb4bb7a565b3d33609ec67acd40f1ae5addf103378f2ef32ab377f"
            ),
            libPaths = setOf("lib"),
            lombok = lombok1_18_4
    )
    private val openjdk9 = Sdk.OpenJdk(
            name = "OpenJDK 9.0.0",
            distribution = RemoteFile(
                    "https://github.com/AdoptOpenJDK/openjdk9-binaries/releases/download/jdk-9.0.4%2B11/OpenJDK9U-jdk_x64_linux_hotspot_9.0.4_11.tar.gz",
                    sha256 = "aa4fc8bda11741facaf3b8537258a4497c6e0046b9f4931e31f713d183b951f1"
            ),
            libPaths = setOf("lib"),
            lombok = lombok1_18_4
    )
    private val openjdk8 = Sdk.OpenJdk(
            name = "OpenJDK 8u92",
            distribution = RemoteFile(
                    "http://cdn.azul.com/zulu/bin/zulu8.15.0.1-jdk8.0.92-linux_x64.tar.gz",
                    md5 = "509fef886f7c6992d0f6f133c4928ec9"
            ),
            libPaths = setOf("lib/amd64", "lib/amd64/jli"),
            lombok = lombok1_18_4
    )
    private val openjdk7 = Sdk.OpenJdk(
            name = "OpenJDK 7u101",
            distribution = RemoteFile(
                    "http://cdn.azul.com/zulu/bin/zulu7.14.0.5-jdk7.0.101-linux_x64.tar.gz",
                    md5 = "6fd6af8bc9a696116b7eeff8d28b0e98"
            ),
            libPaths = setOf("lib/amd64", "lib/amd64/jli"),
            lombok = lombok1_18_4
    )
    private val openjdk6 = Sdk.OpenJdk(
            name = "OpenJDK 6u79",
            distribution = RemoteFile(
                    "http://cdn.azul.com/zulu/bin/zulu6.12.0.2-jdk6.0.79-linux_x64.tar.gz",
                    md5 = "a54fb082c89de3909df5b69cd50a2155"
            ),
            libPaths = setOf("lib/amd64", "lib/amd64/jli"),
            lombok = lombok1_18_4
    )
    private val openjdk = listOf(openjdk13, openjdk12, openjdk11, openjdk10, openjdk9, openjdk8, openjdk7, openjdk6)
    val defaultJava = openjdk13

    private val ecj4_5 = Sdk.Ecj(
            name = "Eclipse ECJ 4.5.1",
            compilerJar = RemoteFile("https://repo1.maven.org/maven2/org/eclipse/jdt/core/compiler/ecj/4.5.1/ecj-4.5.1.jar"),
            lombok = lombok1_18_4,
            hostJdk = openjdk8
    )
    private val ecj = listOf(ecj4_5)

    private val kotlin1_3_50 = Sdk.KotlinDistribution(
            name = "Kotlin 1.3.50",
            distribution = RemoteFile("https://github.com/JetBrains/kotlin/releases/download/v1.3.50/kotlin-compiler-1.3.50.zip"),
            hostJdk = openjdk8,
            coroutines = RemoteFile("https://repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-core/1.3.2/kotlinx-coroutines-core-1.3.2.jar")
    )
    private val kotlin1_3_10 = Sdk.KotlinDistribution(
            name = "Kotlin 1.3.10",
            distribution = RemoteFile("https://github.com/JetBrains/kotlin/releases/download/v1.3.10/kotlin-compiler-1.3.10.zip"),
            hostJdk = openjdk8,
            coroutines = RemoteFile("https://repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-core/1.0.1/kotlinx-coroutines-core-1.0.1.jar")
    )
    private val kotlin1_2 = Sdk.KotlinDistribution(
            name = "Kotlin 1.2.30",
            distribution = RemoteFile("https://github.com/JetBrains/kotlin/releases/download/v1.2.31/kotlin-compiler-1.2.31.zip"),
            hostJdk = openjdk8,
            coroutines = RemoteFile("https://repo1.maven.org/maven2/org/jetbrains/kotlinx/kotlinx-coroutines-core/0.30.2/kotlinx-coroutines-core-0.30.2.jar")
    )

    private fun kotlinJar(version: String) = Sdk.KotlinJar(
            name = "Kotlin $version",
            compilerJar = RemoteFile("https://repo1.maven.org/maven2/org/jetbrains/kotlin/kotlin-compiler/$version/kotlin-compiler-$version.jar"),
            hostJdk = openjdk8
    )

    private val kotlin1_1_4 = kotlinJar("1.1.4-3")
    private val kotlin1_1_1 = kotlinJar("1.1.1")
    private val kotlin1_0_6 = kotlinJar("1.0.6")
    private val kotlin1_0_5 = kotlinJar("1.0.5")
    private val kotlin1_0_4 = kotlinJar("1.0.4")
    private val kotlin1_0_3 = kotlinJar("1.0.3")
    private val kotlin1_0_2 = kotlinJar("1.0.2")

    private val kotlin = listOf(
            kotlin1_3_50, kotlin1_3_10, kotlin1_2, kotlin1_1_4, kotlin1_1_1, kotlin1_0_6, kotlin1_0_5, kotlin1_0_4,
            kotlin1_0_3, kotlin1_0_2)
    private val defaultKotlin = kotlin1_3_50

    private fun scalaSdk(version: String) = Sdk.Scala(
            name = "Scala $version",
            sdk = RemoteFile("https://downloads.lightbend.com/scala/$version/scala-$version.zip"),
            hostJdk = openjdk8
    )

    private val scala2_13 = scalaSdk("2.13.1")
    private val scala2_12_5 = scalaSdk("2.12.5")
    private val scala2_12_0 = scalaSdk("2.12.0")
    private val scala2_11_8 = scalaSdk("2.11.8")

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
    val sdksByName = (openjdk + ecj + kotlin + scala).associateBy { it.name }
}