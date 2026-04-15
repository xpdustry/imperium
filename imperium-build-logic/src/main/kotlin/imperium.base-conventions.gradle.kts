import org.jetbrains.kotlin.gradle.dsl.JvmTarget

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
    xpdustryMindustry()
    xpdustryReleases()
    xpdustrySnapshots()
    maven("https://oss.sonatype.org/content/repositories/snapshots/") {
        name = "sonatype-snapshots"
        mavenContent { snapshotsOnly() }
    }
}

kotlin {
    jvmToolchain(libs.versions.java.get().toInt())
    coreLibrariesVersion = libs.versions.kotlin.core.get()
    compilerOptions {
        javaParameters = true
        jvmTarget = JvmTarget.fromTarget(libs.versions.java.get())
        freeCompilerArgs.add("-XXLanguage:+UnnamedLocalVariables") // TODO Remove when it hits stable
    }
}

dependencies {
    compileOnlyApi(kotlin("stdlib"))
    compileOnlyApi(kotlin("reflect"))
    testImplementation(kotlin("stdlib"))
    testImplementation(kotlin("reflect"))

    testImplementation(libs.junit.api)
    testRuntimeOnly(libs.junit.engine)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    testImplementation(libs.testcontainers)
    testImplementation(libs.testcontainers.junit.jupiter)
    testImplementation(libs.testcontainers.mariadb)
}

spotless {
    kotlin {
        ktfmt().kotlinlangStyle().configure { it.setMaxWidth(120) }
        licenseHeader("// SPDX-License-Identifier: GPL-3.0-only")
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        ktlint().editorConfigOverride(mapOf("max_line_length" to "120"))
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
