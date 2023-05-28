plugins {
    id("foundation.kotlin-conventions")
    id("foundation.publishing-conventions")
}

dependencies {
    api(projects.foundationCommon)
    runtimeOnly(kotlin("stdlib"))
}
