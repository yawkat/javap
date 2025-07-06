plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
}

kotlin {
    jvm {
        compilations.all {
            kotlinOptions.jvmTarget = "21"
        }

        testRuns["test"].executionTask.configure {
            useTestNG()
        }
    }

    js {
        browser()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.kotlin:kotlin-stdlib:${Versions.kotlin}")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.kotlinxSerialization}")
            }
        }

        val jvmTest by getting {
            dependencies {
                implementation("org.testng:testng:${Versions.testng}")
            }
        }
    }
}

