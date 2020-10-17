plugins {
    kotlin("jvm") version Versions.kotlin
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.4.10")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-runtime:1.0.0")
    testImplementation("org.testng:testng:6.9.10")
}

tasks {
    test {
        useTestNG()
    }
}
