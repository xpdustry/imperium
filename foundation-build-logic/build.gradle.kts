plugins {
    `kotlin-dsl`
    // RANT: The fact the version catalog isn't accessible here annoys me a lot
    id("com.diffplug.spotless") version "6.19.0"
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
    implementation(libs.kotlin.gradle)
    // https://github.com/gradle/gradle/issues/15383#issuecomment-779893192
    implementation(files(libs.javaClass.superclass.protectionDomain.codeSource.location))
}

kotlin {
    jvmToolchain(17)
}
