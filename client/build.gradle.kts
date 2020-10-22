plugins {
    kotlin("js")
    kotlin("plugin.serialization")
}

kotlin {
    js {
        browser()
    }
}

dependencies {
    implementation(project(":shared"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib-js:${Versions.kotlin}")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json-js:${Versions.kotlinxSerialization}")
    implementation("org.webjars:jquery:${Versions.jquery}")
    implementation("org.webjars:jquery-ui:${Versions.jqueryUi}")
    implementation("org.webjars.bower:ace-builds:${Versions.bower}")
}
