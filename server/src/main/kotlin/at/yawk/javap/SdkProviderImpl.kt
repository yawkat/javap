/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package at.yawk.javap

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonUnwrapped
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
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
import java.util.NoSuchElementException
import java.util.zip.ZipInputStream
import javax.inject.Singleton

private val log = LoggerFactory.getLogger(SdkProviderImpl::class.java)

private data class Config(
        val defaults: Map<SdkLanguage, String>,
        val sdks: Map<String, SdkConfig>
)

@Singleton
class SdkProviderImpl() : SdkProvider {
    override lateinit var defaultSdkByLanguage: Map<SdkLanguage, Sdk>
    override lateinit var sdks: List<Sdk>

    fun start() {
        val root = Paths.get("sdk")
        Files.createDirectories(root)

        val config = YAMLMapper().findAndRegisterModules()
                .readValue(SdkProviderImpl::class.java.getResource("sdk.yml"), Config::class.java)
        val downloaded = mutableMapOf<String, Sdk>()
        config.sdks.mapValuesTo(downloaded) {
            it.value.buildSdk(it.key,
                    root.resolve(it.key),
                    { (downloaded[it] ?: throw NoSuchElementException(it)).hostJdk })
        }
        sdks = downloaded.values.toList()
        defaultSdkByLanguage = config.defaults.mapValues {
            val sdk = downloaded[it.value] ?: throw Exception("Invalid SDK reference ${it.value} for language ${it.key} (we have ${downloaded.values} available)")
            if (sdk.language != it.key) throw Exception("Language mismatch with ${it.value} (expected ${it.key})")
            sdk
        }
    }
}

private data class RemoteFile(
        val url: URL,
        val md5: String? = null,
        val sha256: String? = null,
        val sha512: String? = null
) {
    fun <R> download(consumer: (InputStream) -> R): R {
        log.info("Fetching {}", url)
        return url.openStream().use {
            var stream: InputStream = BufferedInputStream(it)
            var callbacks = emptyList<() -> Unit>()

            fun validate(hash: String, hashFunction: HashFunction) {
                val hashing = HashingInputStream(hashFunction, stream)
                stream = hashing
                callbacks += {
                    val actualHash = hashing.hash().asBytes()
                    if (!Arrays.equals(BaseEncoding.base16().lowerCase().decode(hash), actualHash)) {
                        throw RuntimeException("Hash for $url is invalid (expected $hash but was ${BaseEncoding.base16().lowerCase().encode(actualHash)}, corrupted download?)")
                    }
                }
            }

            if (md5 != null) validate(md5, Hashing.md5())
            if (sha256 != null) validate(sha256, Hashing.sha256())
            if (sha512 != null) validate(sha512, Hashing.sha512())

            val ret = consumer(stream)

            callbacks.forEach { it() }

            ret
        }
    }

    fun downloadTo(path: Path) {
        download { Files.copy(it, path) }
    }
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

@JsonSubTypes(
        JsonSubTypes.Type(value = ZuluSdkConfig::class, name = "zulu"),
        JsonSubTypes.Type(value = EcjConfig::class, name = "ecj"),
        JsonSubTypes.Type(value = KotlinConfig::class, name = "kotlin"),
        JsonSubTypes.Type(value = KotlinDistributionConfig::class, name = "kotlin-distribution"),
        JsonSubTypes.Type(value = ScalaConfig::class, name = "scala")
)
@JsonTypeInfo(property = "type", use = JsonTypeInfo.Id.NAME)
private interface SdkConfig {
    fun buildSdk(name: String, sdkRoot: Path, lookupJdk: (String) -> Jdk): Sdk
}

private class ZuluSdkConfig : SdkConfig {
    lateinit var distribution: RemoteFile
    var lombok: RemoteFile? = null
    lateinit var libPaths: Set<Path>

    override fun buildSdk(name: String,
                          sdkRoot: Path,
                          lookupJdk: (String) -> Jdk): Sdk {
        buildSdkRootIfMissing(sdkRoot) { tmp ->
            val dist = tmp.resolve("dist.tgz")
            try {
                distribution.downloadTo(dist)

                ProcessExecutor()
                        .directory(tmp.toFile())
                        .command("tar", "xzf", dist.toString(), "--strip", "1")
                        .redirectOutput(Slf4jStream.of(log).asInfo())
                        .exitValueNormal()
                        .destroyOnExit()
                        .execute()
            } finally {
                if (Files.exists(dist)) Files.delete(dist)
            }
        }

        var compilerCommand = listOf(
                sdkRoot.resolve("bin/javac").toAbsolutePath().toString()
        )

        if (lombok != null) {
            val lombokLocation = sdkRoot.resolve("lombok-" + BaseEncoding.base16().encode(
                    Hashing.sha256().hashString(lombok!!.url.toExternalForm(), StandardCharsets.UTF_8).asBytes())
                    + ".jar")
            if (!Files.exists(lombokLocation)) {
                lombok!!.downloadTo(lombokLocation)
            }
            compilerCommand += listOf(
                    "-cp", lombokLocation.toAbsolutePath().toString(),
                    "-processor", "lombok.launch.AnnotationProcessorHider\$AnnotationProcessor,lombok.launch.AnnotationProcessorHider\$ClaimingProcessor"
            )
        }

        return Sdk(
                name,
                hostJdk = Jdk(sdkRoot, libPaths.map { sdkRoot.resolve(it) }),
                baseDir = sdkRoot,
                compilerCommand = compilerCommand,
                language = SdkLanguage.JAVA
        )
    }
}

private class EcjConfig : SdkConfig {
    lateinit var compilerJar: RemoteFile
    var lombok: RemoteFile? = null
    lateinit var hostJdk: String

    override fun buildSdk(name: String,
                          sdkRoot: Path,
                          lookupJdk: (String) -> Jdk): Sdk {
        buildSdkRootIfMissing(sdkRoot) { tmp ->
            compilerJar.downloadTo(tmp.resolve("ecj.jar"))
        }

        val javaPath = System.getenv("JAVA_HOME")?.let { it + "/bin/java" } ?: "java"
        var compilerCommand = listOf(javaPath)

        val lombokLocation = sdkRoot.resolve("lombok.jar")
        if (lombok != null) {
            if (!Files.exists(lombokLocation)) {
                lombok!!.downloadTo(lombokLocation)
            }
            compilerCommand += listOf("-javaagent:${lombokLocation.toAbsolutePath()}=ECJ")
        }

        compilerCommand += listOf("-jar", sdkRoot.resolve("ecj.jar").toAbsolutePath().toString(), "-source", "8", "-proceedOnError")

        return Sdk(
                name,
                baseDir = sdkRoot,
                compilerCommand = compilerCommand,
                language = SdkLanguage.JAVA,
                hostJdk = lookupJdk(hostJdk)
        )
    }
}

private class KotlinConfig : SdkConfig {
    @JsonUnwrapped lateinit var compilerJar: RemoteFile
    lateinit var hostJdk: String

    override fun buildSdk(name: String,
                          sdkRoot: Path,
                          lookupJdk: (String) -> Jdk): Sdk {
        buildSdkRootIfMissing(sdkRoot) { tmp ->
            compilerJar.downloadTo(tmp.resolve("kotlin-compiler.jar"))
        }

        val compilerPath = sdkRoot.resolve("kotlin-compiler.jar").toAbsolutePath()
        val host = lookupJdk(hostJdk)
        val javaPath = host.java.toAbsolutePath().toString()
        return Sdk(
                name,
                baseDir = sdkRoot,
                compilerCommand = listOf(javaPath, "-jar", "$compilerPath", "-no-stdlib", "-cp", "$compilerPath:."),
                language = SdkLanguage.KOTLIN,
                hostJdk = host
        )
    }
}

private class KotlinDistributionConfig : SdkConfig {
    @JsonUnwrapped lateinit var distribution: RemoteFile
    lateinit var hostJdk: String
    var coroutines: RemoteFile? = null

    override fun buildSdk(name: String,
                          sdkRoot: Path,
                          lookupJdk: (String) -> Jdk): Sdk {
        val coroutinesPath = sdkRoot.resolve("kotlin-coroutines.jar").toAbsolutePath()
        if (coroutines != null && !Files.exists(coroutinesPath) && Files.exists(sdkRoot)) {
            deleteRecursively(sdkRoot)
        }

        buildSdkRootIfMissing(sdkRoot) { tmp ->
            distribution.download {
                extractZip(it, tmp)
            }

            val compilerPath = tmp.resolve("bin/kotlinc")
            Files.setPosixFilePermissions(compilerPath, Files.getPosixFilePermissions(compilerPath) + PosixFilePermission.OWNER_EXECUTE)

            coroutines?.downloadTo(tmp.resolve("kotlin-coroutines.jar"))
        }

        var compilerCommand = listOf(sdkRoot.resolve("bin/kotlinc").toAbsolutePath().toString())
        if (coroutines != null) {
            compilerCommand += listOf("-cp", coroutinesPath.toString())
        }
        return Sdk(
                name,
                baseDir = sdkRoot,
                compilerCommand = compilerCommand,
                language = SdkLanguage.KOTLIN,
                hostJdk = lookupJdk(hostJdk)
        )
    }
}

private class ScalaConfig : SdkConfig {
    @JsonUnwrapped lateinit var sdk: RemoteFile
    lateinit var hostJdk: String

    override fun buildSdk(name: String,
                          sdkRoot: Path,
                          lookupJdk: (String) -> Jdk): Sdk {
        buildSdkRootIfMissing(sdkRoot) { tmp ->
            sdk.download {
                extractZip(it, tmp)
            }

            val compilerPath = tmp.resolve("bin/scalac")
            Files.setPosixFilePermissions(compilerPath, Files.getPosixFilePermissions(compilerPath) + PosixFilePermission.OWNER_EXECUTE)
        }

        val compilerPath = sdkRoot.resolve("bin/scalac").toAbsolutePath()
        return Sdk(
                name,
                baseDir = sdkRoot,
                compilerCommand = listOf(compilerPath.toString()),
                language = SdkLanguage.SCALA,
                hostJdk = lookupJdk(hostJdk)
        )
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