plugins {
    id("imperium.base-conventions")
    id("imperium.publishing-conventions")
    id("com.github.johnrengelman.shadow")
}

dependencies {
    api(projects.imperiumCommon)
    api(libs.discord4j.core)
    runtimeOnly(kotlin("stdlib"))
    runtimeOnly(libs.slf4j.simple)
}

tasks.shadowJar {
    archiveFileName.set("imperium-discord.jar")
    manifest {
        attributes(
            "Main-Class" to "com.xpdustry.imperium.discord.ImperiumDiscordKt",
            "Implementation-Title" to "ImperiumDiscord",
            "Implementation-Version" to project.version,
            "Implementation-Vendor" to "Xpdustry",
        )
    }
    doFirst {
        val file = temporaryDir.resolve("VERSION.txt")
        file.writeText(project.version.toString())
        from(file)
    }
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.register("getArtifactPath") {
    doLast { println(tasks.shadowJar.get().archiveFile.get().toString()) }
}

tasks.register<JavaExec>("runImperiumDiscord") {
    workingDir = temporaryDir
    dependsOn(tasks.shadowJar)
    classpath(tasks.shadowJar)
    description = "Starts a local Imperium discord bot"
    standardInput = System.`in`
}
