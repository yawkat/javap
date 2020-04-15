/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package at.yawk.javap

import at.yawk.javap.model.CompilerConfiguration
import at.yawk.javap.model.ConfigProperties
import com.google.common.hash.HashFunction
import com.google.common.hash.Hashing
import com.google.common.hash.HashingInputStream
import com.google.common.io.BaseEncoding
import org.slf4j.LoggerFactory
import org.zeroturnaround.exec.ProcessExecutor
import org.zeroturnaround.exec.stream.slf4j.Slf4jStream
import java.io.BufferedInputStream
import java.io.InputStream
import java.net.URL
import java.nio.charset.StandardCharsets
import java.nio.file.DirectoryNotEmptyException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.PosixFilePermission
import java.util.Arrays
import java.util.zip.ZipInputStream

private val log = LoggerFactory.getLogger(SdkProviderImpl::class.java)

class SdkProviderImpl : SdkProvider {
    private lateinit var sdks: Map<Sdk, RunnableSdk>

    private val root = Paths.get("sdk")

    private abstract inner class HostedSdk(sdk: Sdk, val sdkRoot: Path, val jdk: Sdk.OpenJdk) : RunnableSdk(sdk) {
        override val jdkHome: Path
            get() = lookupSdk(jdk).jdkHome
        override val readable: Set<Path>
            get() = lookupSdk(jdk).readable + listOf(sdkRoot)
        override val libraryPath: List<Path>
            get() = lookupSdk(jdk).libraryPath
    }

    fun start() {
        Files.createDirectories(root)

        sdks = Sdks.sdksByName.values.associateWith { sdk ->
            val sdkDir = root.resolve(sdk.name)
            prepareSdk(sdk, sdkDir)
        }
    }

    override fun lookupSdk(sdk: Sdk) = sdks.getValue(sdk)

    private fun prepareSdk(sdk: Sdk, sdkRoot: Path) = when (sdk) {
        is Sdk.OpenJdk -> {
            buildSdkRootIfMissing(sdkRoot) { tmp ->
                val dist = tmp.resolve("dist.tgz")
                try {
                    sdk.distribution.downloadTo(dist)

                    ProcessExecutor()
                            .directory(tmp.toFile())
                            .command("tar", "xzf", dist.toString(), "--transform", "s|^\\./||", "--strip", "1")
                            .redirectOutput(Slf4jStream.of(log).asInfo())
                            .exitValueNormal()
                            .destroyOnExit()
                            .execute()
                } finally {
                    if (Files.exists(dist)) Files.delete(dist)
                }
            }

            @Suppress("UnstableApiUsage")
            val lombokLocation = sdkRoot.resolve("lombok-" + BaseEncoding.base16().encode(
                    Hashing.sha256().hashString(sdk.lombok.url, StandardCharsets.UTF_8).asBytes())
                    + ".jar")
            if (!Files.exists(lombokLocation)) {
                sdk.lombok.downloadTo(lombokLocation)
            }

            object : RunnableSdk(sdk) {
                override val jdkHome: Path
                    get() = sdkRoot
                override val libraryPath: List<Path> = sdk.libPaths.map { sdkRoot.resolve(it) }
                override val readable = setOf(sdkRoot)

                override fun compilerCommand(inputFile: Path, outputDir: Path, config: CompilerConfiguration): List<String> {
                    val command = mutableListOf(
                            jdkHome.resolve("bin/javac").toAbsolutePath().toString(),
                            "-encoding", "utf-8",
                            "-d", outputDir.toString()
                    )

                    command.addAll(ConfigProperties.validateAndBuildCommandLine(sdk, config))
                    if (ConfigProperties.lombok.get(config)) {
                        command.addAll(listOf(
                                "-cp", lombokLocation.toAbsolutePath().toString(),
                                "-processor",
                                "lombok.launch.AnnotationProcessorHider\$AnnotationProcessor,lombok.launch.AnnotationProcessorHider\$ClaimingProcessor"
                        ))
                    }

                    command.add(inputFile.toString())
                    return command
                }
            }
        }
        is Sdk.Ecj -> {
            buildSdkRootIfMissing(sdkRoot) { tmp ->
                sdk.compilerJar.downloadTo(tmp.resolve("ecj.jar"))
            }

            val lombokLocation = sdkRoot.resolve("lombok.jar")
            if (!Files.exists(lombokLocation)) {
                sdk.lombok.downloadTo(lombokLocation)
            }

            object : HostedSdk(sdk, sdkRoot, sdk.hostJdk) {
                override fun compilerCommand(inputFile: Path, outputDir: Path, config: CompilerConfiguration) = listOf(
                        jdkHome.resolve("bin/java").toAbsolutePath().toString(),
                        "-javaagent:${lombokLocation.toAbsolutePath()}=ECJ",
                        "-jar", sdkRoot.resolve("ecj.jar").toAbsolutePath().toString(),
                        "-source", "8", "-proceedOnError",
                        "-d", outputDir.toString(), inputFile.toString()
                )
            }
        }
        is Sdk.KotlinJar -> {
            buildSdkRootIfMissing(sdkRoot) { tmp ->
                sdk.compilerJar.downloadTo(tmp.resolve("kotlin-compiler.jar"))
            }

            val compilerPath = sdkRoot.resolve("kotlin-compiler.jar").toAbsolutePath()
            object : HostedSdk(sdk, sdkRoot, sdk.hostJdk) {
                override fun compilerCommand(inputFile: Path, outputDir: Path, config: CompilerConfiguration) = listOf(
                        jdkHome.resolve("bin/java").toAbsolutePath().toString(),
                        "-jar", compilerPath.toString(),
                        "-no-stdlib", "-cp", "$compilerPath",
                        "-d", outputDir.toString(), inputFile.toString()
                )
            }
        }
        is Sdk.KotlinDistribution -> {
            val coroutinesPath = sdkRoot.resolve("kotlin-coroutines.jar").toAbsolutePath()
            if (!Files.exists(coroutinesPath) && Files.exists(sdkRoot)) {
                deleteRecursively(sdkRoot)
            }

            buildSdkRootIfMissing(sdkRoot) { tmp ->
                sdk.distribution.download {
                    extractZip(it, tmp)
                }

                val compilerPath = tmp.resolve("bin/kotlinc")
                Files.setPosixFilePermissions(compilerPath,
                        Files.getPosixFilePermissions(compilerPath) + PosixFilePermission.OWNER_EXECUTE)

                sdk.coroutines.downloadTo(tmp.resolve("kotlin-coroutines.jar"))
            }
            object : HostedSdk(sdk, sdkRoot, sdk.hostJdk) {
                override fun compilerCommand(inputFile: Path, outputDir: Path, config: CompilerConfiguration) = listOf(
                        sdkRoot.resolve("bin/kotlinc").toAbsolutePath().toString(),
                        "-cp", coroutinesPath.toString(),
                        "-d", outputDir.toString(), inputFile.toString()
                )
            }
        }
        is Sdk.Scala -> {
            buildSdkRootIfMissing(sdkRoot) { tmp ->
                sdk.sdk.download {
                    extractZip(it, tmp)
                }

                val compilerPath = tmp.resolve("bin/scalac")
                Files.setPosixFilePermissions(compilerPath,
                        Files.getPosixFilePermissions(compilerPath) + PosixFilePermission.OWNER_EXECUTE)
            }

            object : HostedSdk(sdk, sdkRoot, sdk.hostJdk) {
                override fun compilerCommand(inputFile: Path, outputDir: Path, config: CompilerConfiguration) = listOf(
                        sdkRoot.resolve("bin/scalac").toAbsolutePath().toString(),
                        "-d", outputDir.toString(), inputFile.toString()
                )
            }
        }
    }
}

@Suppress("UnstableApiUsage", "SuspiciousCollectionReassignment")
private fun <R> RemoteFile.download(consumer: (InputStream) -> R): R {
    log.info("Fetching {}", url)
    return URL(url).openStream().use {
        var stream: InputStream = BufferedInputStream(it)
        var callbacks = emptyList<() -> Unit>()

        fun validate(hash: String, hashFunction: HashFunction) {
            val hashing = HashingInputStream(hashFunction, stream)
            stream = hashing
            callbacks += {
                val actualHash = hashing.hash().asBytes()
                if (!Arrays.equals(BaseEncoding.base16().lowerCase().decode(hash), actualHash)) {
                    throw RuntimeException("Hash for $url is invalid (expected $hash but was ${BaseEncoding.base16().lowerCase().encode(
                            actualHash)}, corrupted download?)")
                }
            }
        }

        @Suppress("DEPRECATION")
        if (md5 != null) validate(md5!!, Hashing.md5())
        if (sha256 != null) validate(sha256!!, Hashing.sha256())
        if (sha512 != null) validate(sha512!!, Hashing.sha512())

        val ret = consumer(stream)

        callbacks.forEach { it() }

        ret
    }
}

private fun RemoteFile.downloadTo(path: Path) {
    download { Files.copy(it, path) }
}

private fun moveDirectory(src: Path, target: Path) {
    try {
        Files.move(src, target)
    } catch (e: DirectoryNotEmptyException) {
        Files.walkFileTree(src, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.createDirectories(target.resolve(src.relativize(dir)))
                return FileVisitResult.CONTINUE
            }

            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                Files.move(file, target.resolve(src.relativize(file)))
                return FileVisitResult.CONTINUE
            }
        })
    }
}

private fun buildSdkRootIfMissing(sdkRoot: Path, builder: (Path) -> Unit) {
    if (!Files.exists(sdkRoot)) {
        log.info("Downloading $sdkRoot")

        val tmp = Files.createTempDirectory(null)
        try {
            builder.invoke(tmp)
            moveDirectory(tmp, sdkRoot)
        } finally {
            if (Files.exists(tmp)) {
                deleteRecursively(tmp)
            }
        }
    }
}

private fun extractZip(it: InputStream, out: Path) {
    ZipInputStream(it).use { stream ->
        while (true) {
            val entry = stream.nextEntry ?: break
            if (entry.isDirectory) continue
            var fileName = entry.name
            if (fileName.startsWith("/")) fileName = fileName.substring(1)
            // strip leading scala-a.b.c folder
            fileName = fileName.substring(fileName.indexOf('/') + 1)

            val relativePath = Paths.get(fileName)
            val target = out.resolve(relativePath)
            Files.createDirectories(target.parent)
            Files.copy(stream, target)
        }
    }
}