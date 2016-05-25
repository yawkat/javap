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

class LocalProcessor : Processor {
    override fun process(input: ProcessingInput): ProcessingOutput {
        val tempDirectory = Files.createTempDirectory(null)
        try {
            val sourceDir = tempDirectory.resolve("source")
            Files.createDirectories(sourceDir)
            val classDir = tempDirectory.resolve("classes")
            Files.createDirectories(classDir)

            val sourceFile = sourceDir.resolve("Main.java")
            Files.write(sourceFile, input.code.toByteArray(Charsets.UTF_8))

            val javacResult = ProcessExecutor().command(
                    "javac",
                    "-encoding", "utf-8",
                    "-g", // debugging info
                    "-proc:none", // no annotation processing
                    "-source", "1.8",
                    "-target", "1.8",
                    "-d", classDir.toAbsolutePath().toString(),
                    sourceFile.fileName.toString()
            ).directory(sourceDir.toFile()).readOutput(true).destroyOnExit().execute()

            val javapOutput = if (javacResult.exitValue == 0) {
                val classFiles = Files.list(classDir).use { it.map { it.fileName.toString() }.collect(Collectors.toList<String>()) }
                if (!classFiles.isEmpty()) {
                    ProcessExecutor().command(
                            "javap", "-private", "-l", "-s", "-c", "-constants",
                            *classFiles.toTypedArray()
                    ).directory(classDir.toFile()).readOutput(true).destroyOnExit().execute().outputUTF8()
                } else NO_CLASSES_GENERATED
            } else null

            return ProcessingOutput(javacResult.outputUTF8(), javapOutput)
        } finally {
            deleteRecursively(tempDirectory)
        }
    }

    private fun deleteRecursively(path: Path) {
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
}