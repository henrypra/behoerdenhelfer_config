plugins {
    kotlin("jvm") version "2.2.0"
    kotlin("plugin.serialization") version "2.2.0"
    application
    id("com.diffplug.spotless") version "7.0.4"
}

group = "de.behoerdenhelfer"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.apache.pdfbox:pdfbox:2.0.34")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.13.3")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    jvmToolchain(17)
}

application {
    mainClass.set("de.behoerdenhelfer.content.MainKt")
}

tasks.test {
    useJUnitPlatform()
}

spotless {
    kotlin {
        ktlint()
    }
    kotlinGradle {
        ktlint()
    }
}

fun contentTask(
    name: String,
    description: String,
) = tasks.register<JavaExec>(name) {
    group = "content"
    this.description = description
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("de.behoerdenhelfer.content.MainKt")
    args(name)
    workingDir = projectDir
}

contentTask("validate", "Validates everything under content/ without producing output.")
contentTask("generate", "Validates content/ and writes the deployable file tree to dist/.")
