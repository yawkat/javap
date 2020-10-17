plugins {
    kotlin("js")
}

kotlin {
    js {
        browser()
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.kotlinxSerialization}")
    compileOnly("org.jetbrains.kotlin:kotlin-stdlib-js:${Versions.kotlin}")
}
