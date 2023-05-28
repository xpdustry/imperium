import net.ltgt.gradle.errorprone.CheckSeverity
import net.ltgt.gradle.errorprone.errorprone

plugins {
    id("com.diffplug.spotless")
    id("net.kyori.indra")
    id("net.kyori.indra.licenser.spotless")
    id("net.ltgt.errorprone")
}

// expose version catalog
val libs = extensions.getByType(org.gradle.accessors.dm.LibrariesForLibs::class)

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(libs.checkerframework)
    annotationProcessor(libs.nullaway)
    errorprone(libs.errorprone.core)

    testImplementation(libs.junit.api)
    testRuntimeOnly(libs.junit.engine)
}

indra {
    javaVersions {
        target(libs.versions.java.get().toInt())
        minimumToolchain(libs.versions.java.get().toInt())
    }
}

indraSpotlessLicenser {
    licenseHeaderFile(rootProject.file("LICENSE_HEADER.md"))
}

spotless {
    java {
        palantirJavaFormat("2.30.0") // This version supports the non-sealed keyword
        formatAnnotations()
        importOrderFile(rootProject.file(".spotless/foundation.importorder"))
        applyCommon()
    }
    kotlinGradle {
        ktlint(libs.versions.klint.get())
        applyCommon()
    }
}

tasks.withType<JavaCompile> {
    options.errorprone {
        disableWarningsInGeneratedCode.set(true)
        disable(
            "MissingSummary",
            "BadImport",
            "FutureReturnValueIgnored",
            "InlineMeSuggester",
            "EmptyCatch",
        )
        if (!name.contains("test", true)) {
            check("NullAway", CheckSeverity.ERROR)
            option("NullAway:AnnotatedPackages", "com.xpdustry.foundation")
            option("NullAway:TreatGeneratedAsUnannotated", true)
        }
        excludedPaths.set(".*/build/generated/.*")
    }
}
