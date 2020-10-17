plugins {
    kotlin("multiplatform") version Versions.kotlin
    application
    kotlin("plugin.serialization") version Versions.kotlin
}

kotlin {
    jvm()

    js{
        browser()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlin:kotlin-stdlib:${Versions.kotlin}")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.kotlinxSerialization}")
            }
        }

        val commonTest by getting {
            dependencies {
                implementation("org.testng:testng:${Versions.testng}")
            }
        }
    }
}

tasks {
    test {
        useTestNG()
    }
}
