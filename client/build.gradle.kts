plugins {
    kotlin("js")
    kotlin("plugin.serialization")
}

kotlin {
    js {
        browser {
            binaries.executable()
        }
    }
}

dependencies {
    implementation(project(":shared"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib-js:${Versions.kotlin}")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json-js:${Versions.kotlinxSerialization}")
}
