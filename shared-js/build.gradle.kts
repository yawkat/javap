plugins {
    kotlin("js") version Versions.kotlin
}

kotlin {
    js {
        browser()
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime-js:1.0.0")
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib-js:1.4.10")
}
