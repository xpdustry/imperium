plugins {
    id("imperium.base-conventions")
    id("imperium.publishing-conventions")
    id("com.github.johnrengelman.shadow")
    id("fr.xpdustry.toxopid")
}

dependencies {
    implementation("com.github.Anuken.Mindustry:core:v${libs.versions.mindustry.get()}")
    implementation("com.github.Anuken.Arc:arc-core:v${libs.versions.mindustry.get()}")
    api(projects.imperiumCommon)
    api(libs.discord4j.core)
    runtimeOnly(kotlin("stdlib"))
    runtimeOnly(libs.slf4j.simple)
}

project.afterEvaluate {
    project.configurations.all {
        resolutionStrategy.eachDependency {
            if (requested.group == "com.github.Anuken.Arc") {
                useVersion("v" + libs.versions.mindustry.get())
            }
        }
    }
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

    mergeServiceFiles()
    minimize {
        exclude(dependency("org.jetbrains.kotlin:kotlin-.*:.*"))
        exclude(dependency("org.slf4j:slf4j-.*:.*"))
        exclude(dependency("com.discord4j:discord4j-.*:.*"))
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
