import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.ByteArrayOutputStream
import javax.inject.Inject

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Generates the SDK metadata from the nix flake (`nix/sdks.nix`) as a kotlin source file. This only evaluates the
 * metadata, no SDK is built.
 */
abstract class GenerateSdkMetadata : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val nixFiles: ConfigurableFileCollection

    @get:Internal
    abstract val flakeDirectory: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    @get:Inject
    abstract val execOperations: ExecOperations

    @TaskAction
    fun generate() {
        val json = ByteArrayOutputStream()
        execOperations.exec {
            workingDir = flakeDirectory.get().asFile
            commandLine("nix", "--extra-experimental-features", "nix-command flakes", "eval", "--json", ".#sdkMetadata")
            standardOutput = json
        }
        // split into chunks to stay below the class file constant size limit
        val chunks = json.toString(Charsets.UTF_8).trim().chunked(10000).joinToString(",\n    ") { chunk ->
            "\"" + chunk.replace("\\", "\\\\").replace("\"", "\\\"").replace("$", "\\$") + "\""
        }
        val file = outputDirectory.get().file("at/yawk/javap/SdkMetadata.kt").asFile
        file.parentFile.mkdirs()
        file.writeText("""
            |// generated from nix/sdks.nix, do not edit
            |package at.yawk.javap
            |
            |internal val SDK_METADATA_JSON = listOf(
            |    $chunks
            |).joinToString("")
            |""".trimMargin())
    }
}

val generateSdkMetadata by tasks.registering(GenerateSdkMetadata::class) {
    nixFiles.from(rootProject.file("flake.nix"), rootProject.file("flake.lock"), rootProject.fileTree("nix"))
    flakeDirectory.set(rootProject.layout.projectDirectory)
    outputDirectory.set(layout.buildDirectory.dir("generated/sdkMetadata"))
}

kotlin {
    jvm {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget = JvmTarget.JVM_21
                }
            }
        }

        testRuns["test"].executionTask.configure {
            useTestNG()
        }
    }

    js {
        browser()
    }

    sourceSets {
        commonMain {
            kotlin.srcDir(generateSdkMetadata.flatMap { it.outputDirectory })
            dependencies {
                implementation(libs.kotlin.stdlib)
                implementation(libs.kotlinx.serialization.json)
            }
        }

        jvmTest {
            dependencies {
                implementation(libs.testng)
            }
        }
    }
}
