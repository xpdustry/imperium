plugins {
    id("imperium.base-conventions")
    id("com.github.johnrengelman.shadow")
    id("fr.xpdustry.toxopid")
}

version = rootProject.version

toxopid {
    compileVersion.set(libs.versions.mindustry.map { "v$it" })
}

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
    implementation(libs.cloud.jda5)
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
        val file = temporaryDir.resolve("imperium-version.txt")
        file.writeText(project.version.toString())
        from(file)
    }

    from(rootProject.fileTree("imperium-bundles")) {
        into("com/xpdustry/imperium/bundles/")
    }

    minimize {
        exclude(dependency("org.jetbrains.kotlin:kotlin-.*:.*"))
        exclude(dependency("org.slf4j:slf4j-.*:.*"))
        exclude(dependency("ch.qos.logback:logback-.*:.*"))
        exclude(dependency("org.apache.logging.log4j:log4j-to-slf4j:.*"))
        exclude(dependency("com.sksamuel.hoplite:hoplite-.*:.*"))
        exclude(dependency("org.javacord:javacord-core:.*"))
        exclude(dependency(libs.exposed.jdbc.get()))
        exclude(dependency(libs.mariadb.get()))
        exclude(dependency(libs.caffeine.get()))
        exclude(dependency(libs.prettytime.get()))
        exclude(dependency(libs.time4j.core.get()))
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
