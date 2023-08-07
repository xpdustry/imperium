plugins {
    id("imperium.base-conventions")
    id("imperium.publishing-conventions")
}

dependencies {
    api(libs.mongodb.driver.reactive)
    api(libs.reactor.core)
    api(libs.guava)
    api(libs.gson)
    api(libs.hoplite.core)
    api(libs.hoplite.yaml)
    api(libs.deepl) {
        exclude("org.apache.commons", "commons-math3")
    }
    api(libs.slf4j.api)
    api(libs.password4j)
    api(libs.kryo)
    api(libs.rabbitmq.client)
    api(libs.minio)
    testApi(libs.slf4j.simple)
}
