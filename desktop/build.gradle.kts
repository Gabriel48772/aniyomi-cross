plugins {
    application
    alias(libs.plugins.kotlin.serialization)
    id("org.jetbrains.kotlin.jvm")
}

application {
    mainClass.set("com.aniyomi.cross.desktop.DesktopMainKt")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(project(":core"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.10.2")
}

tasks.register<JavaExec>("generateIcons") {
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.aniyomi.cross.desktop.IconGeneratorKt")
}

tasks.register<Jar>("fatJar") {
    archiveClassifier.set("all")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    manifest {
        attributes["Main-Class"] = "com.aniyomi.cross.desktop.DesktopMainKt"
    }
    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })
}
