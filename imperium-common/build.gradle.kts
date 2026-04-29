plugins {
    id("imperium.base-conventions")
}

dependencies {
    implementation("org.bouncycastle:bcprov-jdk18on:1.84")

    api(libs.guava)
    api(libs.hoplite.core)
    api(libs.hoplite.yaml)
    api(libs.okhttp)
    api(libs.caffeine)

    api(libs.slf4j.api)
    testApi(libs.slf4j.simple)

    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.coroutines.jdk8)
    testApi(libs.kotlinx.coroutines.test)
    api(libs.kotlinx.serialization.json)

    testApi(libs.classgraph)

    api(libs.exposed.core)
    api(libs.exposed.jdbc)
    api(libs.exposed.kotlin.datetime)
    api(libs.exposed.json)
    api(libs.hikari)

    api(libs.prettytime)
    api(libs.time4j.core)
    testRuntimeOnly(libs.h2)
    testRuntimeOnly(libs.mariadb)
    testRuntimeOnly(libs.postgresql)

    implementation(libs.mariadb)
    implementation(libs.postgresql)
}
