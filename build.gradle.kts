allprojects {
    group = "at.yawk.javap"
    version = "1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

plugins {
    kotlin("multiplatform") version Versions.kotlin apply false
    kotlin("plugin.serialization") version Versions.kotlin apply false
    kotlin("js") version Versions.kotlin apply false
    kotlin("jvm") version Versions.kotlin apply false
}
