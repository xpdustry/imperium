plugins {
    id("foundation.base-conventions")
    id("foundation.publishing-conventions")
}

dependencies {
    api(libs.mongodb.driver.reactive)
    api(libs.reactor.core)
    api(libs.guice)
    api(libs.guava)
    api(libs.gson)
    api(libs.hoplite.core)
    api(libs.hoplite.yaml)
    api(libs.deepl) {
        exclude("org.apache.commons", "commons-math3")
    }
    api(libs.slf4j.api)
    api(libs.password4j)
}
