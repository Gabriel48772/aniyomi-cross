plugins {
    `java-library`
    alias(libs.plugins.kotlin.serialization)
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    api(libs.okhttp)
    api(libs.jsoup)
    api(libs.kotlinx.serialization.json)
    api("org.json:json:20240303")
    api("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}
