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
    implementation(libs.errorprone.gradle)
    implementation(libs.kotlin.gradle)
    // https://github.com/KyoriPowered/adventure/blob/b271c100a463a5bdc850753d571bf555ef855b85/build-logic/build.gradle.kts#L17
    compileOnly(files(libs::class.java.protectionDomain.codeSource.location))
}

kotlin {
    jvmToolchain(17)
}
