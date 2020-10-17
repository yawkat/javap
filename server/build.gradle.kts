plugins {
    kotlin("jvm") version Versions.kotlin
}

dependencies {
    implementation(project(":javap-shared"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.4.10")
    implementation("org.zeroturnaround:zt-exec:1.9")
    implementation("com.google.guava:guava:28.2-jre")
    implementation("org.flywaydb:flyway-core:4.0.1")
    implementation("org.bitbucket.mstrobel:procyon-compilertools:0.5.36")
    implementation("org.postgresql:postgresql:42.2.2")
    implementation(project(":javap-client"))
    implementation("io.undertow:undertow-core:2.0.30.Final")
    implementation("com.zaxxer:HikariCP:3.4.2")
    implementation("org.jdbi:jdbi3-core:3.12.2")
    implementation("org.jdbi:jdbi3-sqlobject:3.12.2")
    implementation("ch.qos.logback:logback-classic:1.2.3")
    implementation("com.jakewharton:jax-rs-kotlinx-serialization:0.2.1")
    testImplementation("org.testng:testng:6.9.10")
    testImplementation("org.mockito:mockito-all:2.0.2-beta")
    testImplementation("com.google.jimfs:jimfs:1.1")
    testImplementation("com.h2database:h2:1.4.191")
    testImplementation("net.adoptopenjdk:net.adoptopenjdk.v3.vanilla:0.3.3")
}

tasks {
    test {
        useTestNG()
    }
}
