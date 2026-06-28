plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    js {
        browser {
            binaries.executable()
        }
    }

    sourceSets {
        jsMain {
            kotlin.srcDir("src/main/kotlin")
            resources.srcDir("src/main/resources")

            dependencies {
                implementation(project(":shared"))
                implementation(libs.kotlinx.serialization.json)
            }
        }
    }
}
