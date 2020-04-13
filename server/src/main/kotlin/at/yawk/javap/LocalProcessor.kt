/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package at.yawk.javap

import at.yawk.javap.model.ProcessingInput
import at.yawk.javap.model.ProcessingOutput
import com.google.common.annotations.VisibleForTesting
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

@Suppress("ConvertTryFinallyToUseCall")
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

class LocalProcessor @Inject constructor(private val sdkProvider: SdkProvider,
                                         private val bubblewrap: Bubblewrap) : Processor {
    override fun process(input: ProcessingInput): ProcessingOutput {
        val tempDirectory = Files.createTempDirectory(null)
        try {
            val sourceDir = tempDirectory.resolve("source")
            Files.createDirectories(sourceDir)
            val classDir = tempDirectory.resolve("classes")
            Files.createDirectories(classDir)

            val sdk = Sdks.sdksByName[input.compilerName] ?: throw BadRequestException("Unknown compiler name")
            val runnableSdk = sdkProvider.lookupSdk(sdk)

            val sourceFile = sourceDir.resolve(when (sdk.language) {
                SdkLanguage.JAVA -> "Main.java"
                SdkLanguage.KOTLIN -> "Main.kt"
                SdkLanguage.SCALA -> "Main.scala"
            })

            Files.write(sourceFile, input.code.toByteArray(Charsets.UTF_8))

            val javacResult = bubblewrap.executeCommand(
                    runnableSdk.compilerCommand(sourceFile.fileName, classDir.toAbsolutePath()),
                    workingDir = sourceDir,
                    writable = setOf(tempDirectory),
                    readable = runnableSdk.readable,
                    env = env(runnableSdk)
            )

            var javapOutput: String? = javap(runnableSdk, classDir)
            val procyonOutput: String?
            if (javapOutput == null) {
                if (javacResult.exitValue == 0) {
                    javapOutput = NO_CLASSES_GENERATED
                }
                procyonOutput = null
            } else {
                procyonOutput = decompile(classDir, Decompiler.PROCYON)
            }

            return ProcessingOutput(javacResult.outputUTF8(), javapOutput, procyonOutput)
        } finally {
            deleteRecursively(tempDirectory)
        }
    }

    private fun env(sdk: RunnableSdk) = mapOf(
            "LD_LIBRARY_PATH" to sdk.libraryPath.map { it.toAbsolutePath() }.joinToString(":"),
            "JAVA_HOME" to sdk.jdkHome.toAbsolutePath().toString()
    )

    private fun javap(sdk: RunnableSdk, classDir: Path): String? {
        val classFiles = Files.list(classDir).use { // close stream
            it.sorted().map { classFile -> classFile.fileName.toString() }.collect(Collectors.toList())
        }
        return if (classFiles.isNotEmpty()) {
            bubblewrap.executeCommand(
                    listOf(sdk.jdkHome.resolve("bin/javap").toAbsolutePath().toString(),
                            "-v",
                            "-private",
                            "-constants",
                            "-XDdetails:stackMaps,localVariables") + classFiles,
                    classDir,
                    readable = sdk.readable + listOf(classDir),
                    env = env(sdk)
            ).outputUTF8()
        } else null
    }

    @Suppress("SameParameterValue")
    private fun decompile(classDir: Path, decompiler: Decompiler): String {
        return try {
            decompiler.decompile(classDir)
        } catch(e: Throwable) {
            e.printStackTrace()
            e.toString()
        }
    }
}