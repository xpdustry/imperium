plugins {
    id("imperium.base-conventions")
    application
    id("com.github.johnrengelman.shadow")
    id("io.ktor.plugin")
}

version = rootProject.version

dependencies {
    implementation(projects.imperiumCommon)
    runtimeOnly(kotlin("stdlib"))
    runtimeOnly(kotlin("reflect"))
    implementation(libs.logback.classic)
    implementation(libs.jda) {
        exclude(module = "opus-java")
    }

    implementation("com.github.Anuken.Mindustry:core:v${libs.versions.mindustry.get()}")
    implementation("com.github.Anuken.Arc:arc-core:v${libs.versions.mindustry.get()}")

    implementation("io.ktor:ktor-server-netty-jvm")
    implementation("io.ktor:ktor-server-content-negotiation-jvm")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm")
    implementation("io.ktor:ktor-server-core-jvm")
    implementation("io.ktor:ktor-server-sessions-jvm")
    implementation("io.ktor:ktor-server-auth-jvm")
    implementation("io.ktor:ktor-client-core-jvm")
    implementation("io.ktor:ktor-client-apache-jvm")

    runtimeOnly(libs.mariadb)
    runtimeOnly(libs.h2)
}

application {
    applicationName = "ImperiumDiscord"
    mainClass = "com.xpdustry.imperium.discord.ImperiumDiscordKt"
}

tasks.shadowJar {
    archiveFileName.set("imperium-discord.jar")

    doFirst {
        val file = temporaryDir.resolve("imperium-version.txt")
        file.writeText(project.version.toString())
        from(file)
    }

    minimize {
        exclude(dependency("org.jetbrains.kotlin:kotlin-.*:.*"))
        exclude(dependency("org.slf4j:slf4j-.*:.*"))
        exclude(dependency("ch.qos.logback:logback-.*:.*"))
        exclude(dependency("org.apache.logging.log4j:log4j-to-slf4j:.*"))
        exclude(dependency("com.sksamuel.hoplite:hoplite-.*:.*"))
        exclude(dependency("org.javacord:javacord-core:.*"))
        exclude(dependency("io.ktor:ktor-.*:.*"))
        exclude(dependency(libs.exposed.jdbc.get()))
        exclude(dependency(libs.mariadb.get()))
        exclude(dependency(libs.caffeine.get()))
        exclude(dependency(libs.prettytime.get()))
        exclude(dependency(libs.time4j.core.get()))
        exclude(dependency(libs.h2.get()))
    }
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.register("getArtifactPath") {
    doLast { println(tasks.shadowJar.get().archiveFile.get().toString()) }
}

tasks.runShadow {
    workingDir = temporaryDir
}
