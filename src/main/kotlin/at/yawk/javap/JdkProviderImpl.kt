package at.yawk.javap

import com.google.common.hash.Hashing
import org.slf4j.LoggerFactory
import org.zeroturnaround.exec.ProcessExecutor
import org.zeroturnaround.exec.stream.slf4j.Slf4jStream
import java.io.ByteArrayInputStream
import java.net.URL
import java.nio.file.*
import java.nio.file.attribute.BasicFileAttributes
import java.util.*
import javax.inject.Singleton
import javax.xml.bind.DatatypeConverter

private val log = LoggerFactory.getLogger(JdkProviderImpl::class.java)

@Singleton
class JdkProviderImpl : JdkProvider {
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

    override val defaultJdk = openjdk8u92.jdk
    override val jdks = zuluJdks.map { it.jdk }

    fun downloadMissing() {
        zuluJdks.forEach { it.downloadIfMissing() }
    }

    private class ZuluJdk(
            val name: String,
            val md5: String,
            val url: URL
    ) {
        val jdkRoot = Paths.get("jdk", name)

        val jdk = JdkProvider.Jdk(
                name,
                javacPath = jdkRoot.resolve("bin/javac").toAbsolutePath().toString()
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
}