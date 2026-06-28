import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
    id("com.gradleup.shadow") version "9.4.3"
    id("application")
    kotlin("plugin.serialization")
}

dependencies {
    implementation(project(":shared"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib:${Versions.kotlin}")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.kotlinxSerialization}")
    implementation("org.zeroturnaround:zt-exec:1.12")
    implementation("com.google.guava:guava:33.6.0-jre")
    implementation("org.flywaydb:flyway-core:12.9.0")
    implementation("org.flywaydb:flyway-database-postgresql:12.9.0")
    implementation("org.bitbucket.mstrobel:procyon-compilertools:0.6.0")
    implementation("org.postgresql:postgresql:42.7.11")
    implementation("io.undertow:undertow-core:2.4.2.Final")
    implementation("com.zaxxer:HikariCP:7.1.0")
    implementation("org.jdbi:jdbi3-core:3.53.0")
    implementation("org.jdbi:jdbi3-sqlobject:3.53.0")
    implementation("ch.qos.logback:logback-classic:1.5.37")
    implementation("org.webjars.npm:ace-builds:1.44.0")
    testImplementation("org.testng:testng:${Versions.testng}")
    testImplementation("org.mockito:mockito-core:5.23.0")
    testImplementation("com.google.jimfs:jimfs:1.3.1")
    testImplementation("com.h2database:h2:2.4.240")
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
        from(project(":client").tasks.named("jsBrowserDistribution"))
    }

    shadowJar {
        mergeServiceFiles()
        archiveClassifier.set("shaded")
    }
}
