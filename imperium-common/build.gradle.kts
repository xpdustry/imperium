plugins {
    id("imperium.base-conventions")
    id("imperium.publishing-conventions")
}

dependencies {
    api(libs.mongodb.driver.kotlin.coroutine)
    api(libs.guava)
    api(libs.gson)
    api(libs.hoplite.core)
    api(libs.hoplite.yaml)
    api(libs.deepl) {
        exclude("org.apache.commons", "commons-math3")
    }
    api(libs.slf4j.api)
    api(libs.password4j)
    api(libs.rabbitmq.client)
    api(libs.minio)
    api(libs.snowflake.id)
    api(libs.okhttp)
    runtimeOnly(libs.okio) // Fixes CVE in okhttp

    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.coroutines.jdk8)
    api(libs.kotlinx.serialization.json)
    api(libs.mongodb.bson.kotlinx)

    testApi(libs.slf4j.simple)
    testApi(libs.kotlinx.coroutines.test)
    testApi(libs.kotlinx.serialization.json)
    testApi(libs.mongodb.bson.kotlinx)
    testApi(libs.classgraph)
}
