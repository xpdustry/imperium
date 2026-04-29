import java.net.URI

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

    gpl3OnlyLicense()

    publishReleasesTo("xpdustry", "https://maven.xpdustry.com/releases")

    github("xpdustry", "imperium") {
        ci(true)
        issues(true)
        scm(true)
    }

    configurePublications {
        pom {
            organization {
                name.set("Xpdustry")
                url.set("https://www.xpdustry.com")
            }

            developers {
                developer {
                    id.set("Phinner")
                    timezone.set("Europe/Brussels")
                }

                developer {
                    id.set("ZetaMap")
                    timezone.set("Europe/Paris")
                }

                developer {
                    id.set("L0615T1C5-216AC-9437")
                }
            }
        }
    }
}

repositories {
    mavenCentral()

    maven {
        url = URI.create("https://maven.xpdustry.com/releases")
        name = "xpdustry-releases"
        mavenContent { releasesOnly() }
    }

    maven {
        url = URI.create("https://maven.xpdustry.com/snapshots")
        name = "xpdustry-snapshots"
        mavenContent { snapshotsOnly() }
    }

    maven {
        url = URI.create("https://maven.xpdustry.com/mindustry")
        name = "xpdustry-mindustry"
        mavenContent { releasesOnly() }
    }
}

kotlin {
    compilerOptions {
        javaParameters = true
        freeCompilerArgs.add("-XXLanguage:+UnnamedLocalVariables") // TODO Remove when it hits stable
    }
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("reflect"))

    testImplementation(libs.junit.api)
    testRuntimeOnly(libs.junit.engine)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
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
