plugins {
    id("imperium.base-conventions")
    id("imperium.publishing-conventions")
    id("com.github.johnrengelman.shadow")
}

dependencies {
    implementation(projects.imperiumCommon)
    runtimeOnly(kotlin("stdlib"))
    implementation(libs.logback.classic)
    implementation("org.mongodb:mongodb-driver-sync:4.11.1")
}

tasks.shadowJar {
    archiveFileName.set("imperium-migrator.jar")

    manifest {
        attributes(
            "Main-Class" to "com.xpdustry.imperium.discord.ImperiumDiscordKt",
            "Implementation-Title" to "ImperiumMigrator",
            "Implementation-Version" to project.version,
            "Implementation-Vendor" to "Xpdustry",
        )
    }

    minimize {
        exclude(dependency("org.jetbrains.kotlin:kotlin-.*:.*"))
        exclude(dependency("org.slf4j:slf4j-.*:.*"))
        exclude(dependency("ch.qos.logback:logback-.*:.*"))
        exclude(dependency("com.sksamuel.hoplite:hoplite-.*:.*"))
        exclude(dependency(libs.exposed.jdbc.get()))
        exclude(dependency(libs.mariadb.get()))
        exclude(dependency(libs.caffeine.get()))
    }
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

tasks.register<JavaExec>("runImperiumMigrator") {
    workingDir = temporaryDir
    dependsOn(tasks.shadowJar)
    classpath(tasks.shadowJar)
    description = "Starts a local Imperium discord bot"
    standardInput = System.`in`
}
