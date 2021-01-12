import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    kotlin("jvm")
    id("com.github.johnrengelman.shadow") version "6.1.0"
    id("application")
    kotlin("plugin.serialization")
}

dependencies {
    implementation(project(":shared"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib:${Versions.kotlin}")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.kotlinxSerialization}")
    implementation("org.zeroturnaround:zt-exec:${Versions.ztExec}")
    implementation("com.google.guava:guava:${Versions.guava}")
    implementation("org.flywaydb:flyway-core:${Versions.flyway}")
    implementation("org.bitbucket.mstrobel:procyon-compilertools:${Versions.procyon}")
    implementation("org.postgresql:postgresql:${Versions.postgresql}")
    implementation("io.undertow:undertow-core:${Versions.undertow}")
    implementation("com.zaxxer:HikariCP:${Versions.hikariCP}")
    implementation("org.jdbi:jdbi3-core:${Versions.jdbi}")
    implementation("org.jdbi:jdbi3-sqlobject:${Versions.jdbi}")
    implementation("ch.qos.logback:logback-classic:${Versions.logback}")
    implementation("org.webjars.bower:ace-builds:${Versions.bower}")
    testImplementation("org.testng:testng:${Versions.testng}")
    testImplementation("org.mockito:mockito-all:${Versions.mockito}")
    testImplementation("com.google.jimfs:jimfs:${Versions.jimfs}")
    testImplementation("com.h2database:h2:${Versions.h2}")
    testImplementation("net.adoptopenjdk:net.adoptopenjdk.v3.vanilla:${Versions.adoptOpenJdkVanilla}")
}

application {
    mainClassName = "at.yawk.javap.JavapApplicationKt"
}

tasks {
    compileKotlin {
        kotlinOptions {
            freeCompilerArgs = listOf("-Xjvm-default=enable")
            jvmTarget = "1.8"
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
