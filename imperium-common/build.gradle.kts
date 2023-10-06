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
    api(libs.base32)
    api(libs.okhttp)
    runtimeOnly(libs.okio) // Fixes CVE in okhttp

    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.coroutines.jdk8)

    testApi(libs.slf4j.simple)
    testApi(libs.kotlinx.coroutines.test)
}
