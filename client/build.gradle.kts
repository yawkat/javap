plugins {
    kotlin("js") version Versions.kotlin
}

kotlin {
    js {
        browser()
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib-js:1.4.10")
    implementation(project(":javap-shared-js:"))
    implementation("org.webjars:jquery:2.2.4")
    implementation("org.webjars:jquery-ui:1.11.4")
    implementation("org.webjars.bower:ace-builds:1.4.2")
}
