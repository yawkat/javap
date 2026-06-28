import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.shadow)
    id("application")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(project(":shared"))
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.zt.exec)
    implementation(libs.guava)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.procyon.compilertools)
    implementation(libs.postgresql)
    implementation(libs.undertow.core)
    implementation(libs.hikari.cp)
    implementation(libs.jdbi.core)
    implementation(libs.jdbi.sqlobject)
    implementation(libs.logback.classic)
    implementation(libs.ace.builds)
    testImplementation(libs.testng)
    testImplementation(libs.mockito.core)
    testImplementation(libs.jimfs)
    testImplementation(libs.h2)
    testImplementation(libs.adoptopenjdk.v3.vanilla)
}

application {
    mainClass.set("at.yawk.javap.JavapApplicationKt")
}

tasks {
    compileKotlin {
        compilerOptions {
            freeCompilerArgs.add("-Xjvm-default=all")
            jvmTarget.set(JvmTarget.JVM_21)
        }
    }

    test {
        useTestNG()
    }

    processResources {
        from(project(":client").tasks.named("jsBrowserDistribution"))
    }

    shadowJar {
        mergeServiceFiles()
        archiveClassifier.set("shaded")
    }
}
