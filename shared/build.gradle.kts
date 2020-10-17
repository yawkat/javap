plugins {
    kotlin("jvm") version Versions.kotlin
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:${Versions.kotlin}")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${Versions.kotlinxSerialization}")
    testImplementation("org.testng:testng:${Versions.testng}")
}

tasks {
    test {
        useTestNG()
    }
}
