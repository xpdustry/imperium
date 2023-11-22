plugins {
    org.jetbrains.kotlin.jvm
    org.jetbrains.kotlin.plugin.serialization
    net.kyori.indra
    com.diffplug.spotless
}

indra {
    javaVersions {
        target(libs.versions.java.get().toInt())
    }
}

repositories {
    mavenCentral()
    xpdustryReleases()
    xpdustryMindustry()
}

kotlin {
    jvmToolchain(libs.versions.java.get().toInt())
    coreLibrariesVersion = libs.versions.kotlin.core.get()
    target {
        compilations.configureEach {
            kotlinOptions {
                javaParameters = true // Needed for commands
                jvmTarget = libs.versions.java.get()
                // The kotlin version of libs is in the format x.y.z, but apiVersion needs to be in the format x.y
                apiVersion = libs.versions.kotlin.core.get().split(".").take(2).joinToString(".")
            }
        }
    }
}

dependencies {
    compileOnlyApi(kotlin("stdlib"))
    compileOnlyApi(kotlin("reflect"))
    testImplementation(kotlin("stdlib"))
    testImplementation(kotlin("reflect"))

    testImplementation(libs.junit.api)
    testRuntimeOnly(libs.junit.engine)

    testImplementation(libs.testcontainers)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.rabbitmq)
}

spotless {
    kotlin {
        ktfmt().dropboxStyle()
        licenseHeader(toLongComment(rootProject.file("LICENSE_HEADER.md").readText()))
        indentWithSpaces(4)
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        ktlint(libs.versions.ktlint.get())
    }
}

// Kyori enables javadoc, but we don't want that with a Kotlin project
tasks.javadocJar {
    enabled = false
    outputs.files()
}

fun toLongComment(text: String) = buildString {
    appendLine("/*")
    text.lines().forEach { appendLine(" * ${it.trim()}") }
    appendLine(" */")
}
