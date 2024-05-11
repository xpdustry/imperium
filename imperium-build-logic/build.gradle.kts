plugins {
    `kotlin-dsl`
}

repositories {
    gradlePluginPortal()
}

dependencies {
    implementation(libs.indra.licenser.spotless)
    implementation(libs.indra.common)
    implementation(libs.toxopid)
    implementation(libs.spotless)
    implementation(libs.shadow)
    implementation(libs.kotlin.plugin.core)
    implementation(libs.kotlin.plugin.serialization)
    implementation(libs.ktor.gradle)
    // https://github.com/gradle/gradle/issues/15383#issuecomment-779893192
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}

kotlin {
    jvmToolchain(17)
}
