plugins {
    kotlin("jvm")
    `java-library`
    id("com.diffplug.spotless")
    id("net.kyori.indra.licenser.spotless")
}

// expose version catalog
val libs = extensions.getByType(org.gradle.accessors.dm.LibrariesForLibs::class)

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(libs.versions.java.get().toInt())
    explicitApi()
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
}

indraSpotlessLicenser {
    licenseHeaderFile(rootProject.file("LICENSE_HEADER.md"))
}

spotless {
    kotlin {
        // For some reasons, enabling the linter keeps the license header from being applied
        // ktlint(libs.versions.klint.get())
        applyCommon()
    }
    kotlinGradle {
        ktlint(libs.versions.klint.get())
        applyCommon()
    }
}
