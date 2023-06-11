plugins {
    kotlin("jvm")
    `java-library`
    id("com.diffplug.spotless")
    id("net.kyori.indra.licenser.spotless")
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

indraSpotlessLicenser {
    licenseHeaderFile(rootProject.file("LICENSE_HEADER.md"))
}

spotless {
    kotlin {
        // TODO: For some reasons, enabling the linter keeps the license header from being applied
        // ktlint(libs.versions.klint.get())
        applyCommon()
    }
    kotlinGradle {
        ktlint(libs.versions.klint.get())
        applyCommon()
    }
}

tasks.test {
    useJUnitPlatform()
}
