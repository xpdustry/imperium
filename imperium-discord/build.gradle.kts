plugins {
    id("imperium.base-conventions")
    id("com.gradleup.shadow")
}

version = rootProject.version

dependencies {
    implementation(projects.imperiumCommon)
    implementation(libs.logback.classic)
    implementation(libs.jda) { exclude(module = "opus-java") }
    runtimeOnly(libs.h2)
    runtimeOnly(libs.mariadb)
}

tasks.shadowJar {
    archiveFileName.set("imperium-discord.jar")

    doFirst {
        val file = temporaryDir.resolve("imperium-version.txt")
        file.writeText(project.version.toString())
        from(file)
    }

    minimize {
        exclude(dependency("org.slf4j:slf4j-.*:.*"))
        exclude(dependency("ch.qos.logback:logback-.*:.*"))
        exclude(dependency("com.sksamuel.hoplite:hoplite-.*:.*"))
        exclude(dependency(libs.exposed.jdbc.get()))
        exclude(dependency(libs.mariadb.get()))
        exclude(dependency(libs.caffeine.get()))
        exclude(dependency(libs.prettytime.get()))
        exclude(dependency(libs.time4j.core.get()))
        exclude(dependency(libs.h2.get()))
    }

    manifest {
        attributes(
            "Implementation-Title" to "ImperiumDiscord",
            "Implementation-Version" to project.version,
            "Implementation-Vendor" to "Xpdustry",
            "Main-Class" to "com.xpdustry.imperium.discord.ImperiumDiscordKt",
        )
    }
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.register("getArtifactPath") {
    doLast { println(tasks.shadowJar.get().archiveFile.get().toString()) }
}

tasks.register<JavaExec>("runBackend") {
    workingDir = temporaryDir
    classpath(tasks.shadowJar)
}
