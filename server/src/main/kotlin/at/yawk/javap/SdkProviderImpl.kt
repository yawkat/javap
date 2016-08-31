/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package at.yawk.javap

import com.google.common.hash.Hashing
import org.slf4j.LoggerFactory
import org.zeroturnaround.exec.ProcessExecutor
import org.zeroturnaround.exec.stream.slf4j.Slf4jStream
import java.io.ByteArrayInputStream
import java.net.URL
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.PosixFileAttributeView
import java.nio.file.attribute.PosixFilePermission
import java.util.*
import java.util.zip.ZipInputStream
import javax.inject.Singleton
import javax.xml.bind.DatatypeConverter

private val log = LoggerFactory.getLogger(SdkProviderImpl::class.java)

@Singleton
class SdkProviderImpl : SdkProvider {
    private val openjdk6u79 = ZuluJdk(
            "OpenJDK 6u79",
            "a54fb082c89de3909df5b69cd50a2155",
            URL("http://cdn.azul.com/zulu/bin/zulu6.12.0.2-jdk6.0.79-linux_x64.tar.gz")
    )
    private val openjdk7u101 = ZuluJdk(
            "OpenJDK 7u101",
            "6fd6af8bc9a696116b7eeff8d28b0e98",
            URL("http://cdn.azul.com/zulu/bin/zulu7.14.0.5-jdk7.0.101-linux_x64.tar.gz")
    )
    private val openjdk8u92 = ZuluJdk(
            "OpenJDK 8u92",
            "509fef886f7c6992d0f6f133c4928ec9",
            URL("http://cdn.azul.com/zulu/bin/zulu8.15.0.1-jdk8.0.92-linux_x64.tar.gz")
    )
    private val openjdk9pre = ZuluJdk(
            "OpenJDK 9.0.0.4 EA",
            "e8904591fc029152a38804f3f3c02461",
            URL("http://cdn.azul.com/zulu-pre/bin/zulu9.0.0.4-ea-jdk9.0.0-linux_x64.tar.gz")
    )

    private val zuluJdks = listOf(openjdk9pre, openjdk8u92, openjdk7u101, openjdk6u79)

    private val ecj451 = Ecj(
            "Eclipse ECJ 4.5.1",
            URL("http://central.maven.org/maven2/org/eclipse/jdt/core/compiler/ecj/4.5.1/ecj-4.5.1.jar")
    )

    private val ecjSdks = listOf(ecj451)

    private val kotlin102 = StandardKotlin(
            "Kotlin 1.0.2",
            URL("https://github.com/JetBrains/kotlin/releases/download/1.0.2/kotlin-compiler-1.0.2.zip")
    )

    private val kotlinSdks = listOf(kotlin102)

    override val defaultSdkByLanguage = mapOf(
            SdkLanguage.JAVA to openjdk8u92.sdk,
            SdkLanguage.KOTLIN to kotlin102.sdk
    )
    override val sdks = kotlinSdks.map { it.sdk } + zuluJdks.map { it.sdk } + ecjSdks.map { it.sdk }

    fun downloadMissing() {
        zuluJdks.forEach { it.downloadIfMissing() }
        ecjSdks.forEach { it.downloadIfMissing() }
        kotlinSdks.forEach { it.downloadIfMissing() }
    }

    private class ZuluJdk(
            val name: String,
            val md5: String,
            val url: URL
    ) {
        val jdkRoot = Paths.get("sdk", name)

        val sdk = Sdk(
                name,
                baseDir = jdkRoot,
                compilerCommand = listOf(jdkRoot.resolve("bin/javac").toAbsolutePath().toString()),
                language = SdkLanguage.JAVA
        )

        fun downloadIfMissing() {
            if (Files.exists(jdkRoot)) return

            log.info("Downloading jdk '$name'")
            val tar = url.openStream().use { it.readBytes() }
            if (!Arrays.equals(DatatypeConverter.parseHexBinary(md5), Hashing.md5().hashBytes(tar).asBytes()))
                throw RuntimeException("Hash mismatch for $name (corrupt download?)")

            val tmp = Files.createTempDirectory(null)
            try {
                log.info("Extracting jdk '$name'")
                ProcessExecutor()
                        .directory(tmp.toFile())
                        .command("tar", "xz", "--strip", "1")
                        .redirectInput(ByteArrayInputStream(tar))
                        .redirectOutput(Slf4jStream.of(log).asInfo())
                        .exitValueNormal()
                        .destroyOnExit()
                        .execute()

                if (!Files.exists(jdkRoot.parent)) Files.createDirectories(jdkRoot.parent)
                try {
                    Files.move(tmp, jdkRoot)
                } catch (e: DirectoryNotEmptyException) {
                    Files.walkFileTree(tmp, object : SimpleFileVisitor<Path>() {
                        override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                            Files.createDirectories(jdkRoot.resolve(tmp.relativize(dir)))
                            return FileVisitResult.CONTINUE
                        }

                        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                            Files.move(file, jdkRoot.resolve(tmp.relativize(file)))
                            return FileVisitResult.CONTINUE
                        }
                    })
                }
            } finally {
                if (Files.exists(tmp)) {
                    deleteRecursively(tmp)
                }
            }
        }
    }

    private class Ecj(name: String, val url: URL) {
        val ecjRoot = Paths.get("sdk", name)
        val ecjPath = ecjRoot.resolve("ecj.jar")

        val sdk = Sdk(
                name,
                baseDir = ecjRoot,
                compilerCommand = listOf("java", "-jar", ecjPath.toAbsolutePath().toString(), "-source", "8"),
                language = SdkLanguage.JAVA
        )

        fun downloadIfMissing() {
            if (Files.exists(ecjRoot)) return

            Files.createDirectory(ecjRoot)
            url.openStream().use { Files.copy(it, ecjPath) }
        }
    }

    private class StandardKotlin(val name: String, val url: URL) {
        val sdkRoot = Paths.get("sdk", name)
        val compilerPath = sdkRoot.resolve("bin/kotlinc").toAbsolutePath().toString()

        val sdk = Sdk(
                name,
                baseDir = sdkRoot,
                compilerCommand = listOf(compilerPath),
                language = SdkLanguage.KOTLIN
        )

        fun downloadIfMissing() {
            if (Files.exists(sdkRoot)) return

            log.info("Downloading kotlin sdk '$name'")

            val tmp = Files.createTempDirectory(null)
            try {
                log.info("Extracting kotlin sdk '$name'")

                ZipInputStream(url.openStream()).use { input ->
                    while (true) {
                        val entry = input.nextEntry ?: break
                        if (entry.isDirectory) continue
                        if (!entry.name.startsWith("kotlinc/")) continue
                        val output = sdkRoot.resolve(entry.name.substring("kotlinc/".length))
                        if (!Files.exists(output.parent)) Files.createDirectories(output.parent)
                        Files.copy(input, output)
                    }
                }

                val attributeView = Files.getFileAttributeView(Paths.get(compilerPath), PosixFileAttributeView::class.java)
                attributeView.setPermissions(attributeView.readAttributes().permissions() + PosixFilePermission.OWNER_EXECUTE)
            } finally {
                if (Files.exists(tmp)) {
                    deleteRecursively(tmp)
                }
            }
        }
    }
}