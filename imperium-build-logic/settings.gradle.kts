enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

rootProject.name = "imperium-build-logic"

dependencyResolutionManagement {
    versionCatalogs {
        register("libs") {
            from(files("../gradle/libs.versions.toml")) // include from parent project
        }
    }
}
