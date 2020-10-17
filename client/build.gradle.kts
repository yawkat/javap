plugins {
    kotlin("js") version Versions.kotlin
}

kotlin {
    js {
        browser()
    }
}

dependencies {
    implementation(project(":javap-shared-js:"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib-js:${Versions.kotlin}")
    implementation("org.webjars:jquery:${Versions.jquery}")
    implementation("org.webjars:jquery-ui:${Versions.jqueryUi}")
    implementation("org.webjars.bower:ace-builds:${Versions.bower}")
}
