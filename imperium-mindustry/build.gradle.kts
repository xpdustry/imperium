import fr.xpdustry.toxopid.dsl.mindustryDependencies
import fr.xpdustry.toxopid.task.GithubArtifactDownload
import fr.xpdustry.toxopid.task.MindustryExec

plugins {
    id("imperium.base-conventions")
    id("imperium.publishing-conventions")
    id("fr.xpdustry.toxopid")
    id("com.github.johnrengelman.shadow")
}

val metadata = fr.xpdustry.toxopid.spec.ModMetadata.fromJson(project.file("plugin.json"))
metadata.minGameVersion = libs.versions.mindustry.get()
metadata.description = rootProject.description!!
metadata.version = rootProject.version.toString()

toxopid {
    compileVersion.set(libs.versions.mindustry.map { "v$it" })
    platforms.add(fr.xpdustry.toxopid.spec.ModPlatform.HEADLESS)
}

dependencies {
    api(projects.imperiumCommon)

    mindustryDependencies()
    implementation(libs.distributor4.command.cloud)
    implementation(libs.bundles.cloud)
    compileOnly(libs.distributor4.common)
    compileOnly(libs.distributor4.permission.rank)
    compileOnly(libs.nohorny)
    implementation(libs.jsoup)
}

tasks.shadowJar {
    archiveFileName.set("imperium-mindustry.jar")
    archiveClassifier.set("plugin")

    doFirst {
        val temp = temporaryDir.resolve("plugin.json")
        temp.writeText(metadata.toJson(true))
        from(temp)
    }

    from(rootProject.file("LICENSE.md")) {
        into("META-INF")
    }

    from(rootProject.fileTree("imperium-bundles")) {
        into("com/xpdustry/imperium/bundles/")
    }

    mergeServiceFiles()
    minimize {
        exclude(dependency("com.sksamuel.hoplite:hoplite-.*:.*"))
        exclude(dependency(libs.mariadb.get()))
        exclude(dependency(libs.caffeine.get()))
        exclude(dependency(libs.prettytime.get()))
        exclude(dependency(libs.time4j.core.get()))
        exclude(dependency(libs.h2.get()))
    }
}

configurations.runtimeClasspath {
    exclude("org.jetbrains.kotlin", "kotlin-stdlib")
    exclude("org.jetbrains.kotlin", "kotlin-stdlib-common")
    exclude("org.jetbrains.kotlin", "kotlin-stdlib-jdk8")
    exclude("org.jetbrains.kotlin", "kotlin-reflect")
    exclude("org.jetbrains.kotlinx", "kotlinx-coroutines-core")
    exclude("org.jetbrains.kotlinx", "kotlinx-coroutines-jdk8")
    exclude("org.slf4j")
}

tasks.register("getArtifactPath") {
    doLast { println(tasks.shadowJar.get().archiveFile.get().toString()) }
}

tasks.build { dependsOn(tasks.shadowJar) }

val downloadKotlinRuntime =
    tasks.register<GithubArtifactDownload>("downloadKotlinRuntime") {
        user.set("xpdustry")
        repo.set("kotlin-runtime")
        name.set("kotlin-runtime.jar")
        version.set(
            libs.versions.kotlin.runtime.zip(libs.versions.kotlin.core) { runtime, core ->
                "v$runtime-k.$core"
            },
        )
    }

val downloadDistributor4LoggingSimple =
    tasks.register<GithubArtifactDownload>("downloadDistributor4LoggingSimple") {
        user.set("xpdustry")
        repo.set("distributor")
        name.set("distributor-logging-simple.jar")
        version.set(libs.versions.distributor4.map { "v$it" })
    }

val downloadDistributor4Common =
    tasks.register<GithubArtifactDownload>("downloadDistributor4Common") {
        user.set("xpdustry")
        repo.set("distributor")
        name.set("distributor-common.jar")
        version.set(libs.versions.distributor4.map { "v$it" })
    }

val downloadDistributor4PermissionRank =
    tasks.register<GithubArtifactDownload>("downloadDistributor4PermissionRank") {
        user.set("xpdustry")
        repo.set("distributor")
        name.set("distributor-permission-rank.jar")
        version.set(libs.versions.distributor4.map { "v$it" })
    }

val downloadNoHorny =
    tasks.register<GithubArtifactDownload>("downloadNoHorny") {
        user.set("xpdustry")
        repo.set("nohorny")
        name.set("nohorny.jar")
        version.set(libs.versions.nohorny.map { "v$it" })
    }

tasks.runMindustryClient { mods.setFrom() }

tasks.register<MindustryExec>("runMindustryClient2") {
    group = fr.xpdustry.toxopid.Toxopid.TASK_GROUP_NAME
    classpath(tasks.downloadMindustryClient)
    mainClass.convention("mindustry.desktop.DesktopLauncher")
    modsPath.convention("./mods")
    standardInput = System.`in`
}

tasks.runMindustryServer {
    mods.setFrom(downloadKotlinRuntime, tasks.shadowJar, downloadNoHorny)
    mods.from(rootProject.file("libs").listFiles())
}

// Second server for testing discovery
tasks.register<MindustryExec>("runMindustryServer2") {
    group = fr.xpdustry.toxopid.Toxopid.TASK_GROUP_NAME
    classpath(tasks.downloadMindustryServer)
    mainClass.set("mindustry.server.ServerLauncher")
    modsPath.set("./config/mods")
    standardInput = System.`in`
    mods.setFrom(
        downloadKotlinRuntime,
        tasks.shadowJar,
        downloadNoHorny,
        downloadDistributor4LoggingSimple,
        downloadDistributor4Common,
        downloadDistributor4PermissionRank,
    )
}
