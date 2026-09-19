import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsEnvSpec
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsPlugin
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootExtension
import org.jetbrains.kotlin.gradle.targets.js.nodejs.NodeJsRootPlugin
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnPlugin
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootEnvSpec
import org.jetbrains.kotlin.gradle.targets.js.yarn.YarnRootExtension

allprojects {
    group = "at.yawk.javap"
    version = "1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.shadow) apply false
}

// Offline nix build (see flake.nix) -- use the prebuilt node/yarn from the nix store instead of letting the
// kotlin-js plugins download their own, which the sandboxed build has no network access to do.
System.getenv("NIX_NODEJS_BIN")?.let { nodeBin ->
    plugins.withType<NodeJsPlugin> {
        extensions.findByType<NodeJsEnvSpec>()?.let {
            it.download.set(false)
            it.command.set(nodeBin)
        }
    }
    rootProject.plugins.withType<NodeJsRootPlugin> {
        rootProject.extensions.findByType<NodeJsRootExtension>()?.let {
            it.withGroovyBuilder {
                "setDownload"(false)
                "setNodeCommand"(nodeBin)
            }
        }
    }
}

System.getenv("NIX_YARN_BIN")?.let { yarnBin ->
    plugins.withType<YarnPlugin> {
        extensions.findByType<YarnRootEnvSpec>()?.let {
            it.download.set(false)
            it.command.set(yarnBin)
        }
    }
}
