plugins {
    kotlin("jvm")
    `java-library`
    id("com.diffplug.spotless")
}

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(libs.versions.java.get().toInt())
    coreLibrariesVersion = libs.versions.kotlin.get()
    target {
        compilations.configureEach {
            kotlinOptions {
                jvmTarget = libs.versions.java.get()
            }
        }
    }
}

dependencies {
    compileOnlyApi(kotlin("stdlib"))
    compileOnlyApi(kotlin("reflect"))
    testImplementation(libs.junit.api)
    testRuntimeOnly(libs.junit.engine)
}

spotless {
    kotlin {
        ktlint(libs.versions.ktlint.get())
        licenseHeader(toLongComment(rootProject.file("LICENSE_HEADER.md").readText()))
        indentWithSpaces(4)
        trimTrailingWhitespace()
        endWithNewline()
    }
    kotlinGradle {
        ktlint(libs.versions.ktlint.get())
    }
}

tasks.test {
    useJUnitPlatform()
}

fun toLongComment(text: String) = buildString {
    appendLine("/*")
    text.lines().forEach { appendLine(" * ${it.trim()}") }
    appendLine(" */")
}
