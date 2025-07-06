import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    id("com.github.johnrengelman.shadow") version "8.1.1"
    id("application")
    kotlin("plugin.serialization")
}

dependencies {
    implementation(project(":shared"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib:${Versions.kotlin}")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.kotlinxSerialization}")
    implementation("org.zeroturnaround:zt-exec:1.9")
    implementation("com.google.guava:guava:33.3.0-jre")
    implementation("org.flywaydb:flyway-core:4.0.1")
    implementation("org.bitbucket.mstrobel:procyon-compilertools:0.6.0")
    implementation("org.postgresql:postgresql:42.7.3")
    implementation("io.undertow:undertow-core:2.3.15.Final")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.jdbi:jdbi3-core:3.45.3")
    implementation("org.jdbi:jdbi3-sqlobject:3.45.3")
    implementation("ch.qos.logback:logback-classic:1.5.7")
    implementation("org.webjars.npm:ace-builds:1.35.3")
    testImplementation("org.testng:testng:${Versions.testng}")
    testImplementation("org.mockito:mockito-all:2.0.2-beta")
    testImplementation("com.google.jimfs:jimfs:1.3.0")
    testImplementation("com.h2database:h2:2.3.232")
    testImplementation("net.adoptopenjdk:net.adoptopenjdk.v3.vanilla:0.4.0")
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
        from(project(":client").tasks.named("browserDistribution"))
    }

    shadowJar {
        mergeServiceFiles()
        archiveClassifier.set("shaded")
    }
}
