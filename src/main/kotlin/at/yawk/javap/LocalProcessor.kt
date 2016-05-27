package at.yawk.javap

import at.yawk.javap.model.ProcessingInput
import at.yawk.javap.model.ProcessingOutput
import com.google.common.annotations.VisibleForTesting
import org.zeroturnaround.exec.ProcessExecutor
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.stream.Collectors
import java.util.stream.Stream
import javax.inject.Inject
import javax.ws.rs.BadRequestException

/**
 * @author yawkat
 */
@VisibleForTesting
internal const val NO_CLASSES_GENERATED = "No classes generated"

private inline fun <T, R> Stream<T>.use(f: (Stream<T>) -> R): R {
    try {
        return f(this)
    } finally {
        close()
    }
}

fun deleteRecursively(path: Path) {
    Files.walkFileTree(path, object : SimpleFileVisitor<Path>() {
        override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
            Files.delete(file)
            return FileVisitResult.CONTINUE
        }

        override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
            Files.delete(dir)
            return FileVisitResult.CONTINUE
        }
    })
}

class LocalProcessor @Inject constructor(val jdkProvider: JdkProvider) : Processor {
    override fun process(input: ProcessingInput): ProcessingOutput {
        val tempDirectory = Files.createTempDirectory(null)
        try {
            val sourceDir = tempDirectory.resolve("source")
            Files.createDirectories(sourceDir)
            val classDir = tempDirectory.resolve("classes")
            Files.createDirectories(classDir)

            val sourceFile = sourceDir.resolve("Main.java")

            val jdk = jdkProvider.jdks.find { it.name == input.compilerName }
                    ?: throw BadRequestException("Unknown compiler name")
            Files.write(sourceFile, input.code.toByteArray(Charsets.UTF_8))

            val javacResult = ProcessExecutor().command(
                    jdk.javacPath,
                    "-encoding", "utf-8",
                    "-g", // debugging info
                    "-proc:none", // no annotation processing
                    "-d", classDir.toAbsolutePath().toString(),
                    sourceFile.fileName.toString()
            ).directory(sourceDir.toFile()).readOutput(true).destroyOnExit().execute()

            val javapOutput = if (javacResult.exitValue == 0) {
                javap(classDir)
            } else null

            return ProcessingOutput(javacResult.outputUTF8(), javapOutput)
        } finally {
            deleteRecursively(tempDirectory)
        }
    }

    private fun javap(classDir: Path): String {
        val classFiles = Files.list(classDir).use { it.map { it.fileName.toString() }.collect(Collectors.toList<String>()) }
        return if (!classFiles.isEmpty()) {
            val javapOutput = ProcessExecutor().command(
                    "javap", "-v", "-XDdetails:stackMaps,localVariables",
                    *classFiles.toTypedArray()
            ).directory(classDir.toFile()).readOutput(true).destroyOnExit().execute().outputUTF8()
            javapOutput
                    .replace("\nConstant pool:(\n\\s*#\\d+ =.*)*".toRegex(RegexOption.MULTILINE), "")
                    .replace("Classfile .*\n  Last modified.*\n  MD5.*\n  ".toRegex(RegexOption.MULTILINE), "")
        } else NO_CLASSES_GENERATED
    }
}