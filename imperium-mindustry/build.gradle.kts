import com.xpdustry.toxopid.Toxopid
import com.xpdustry.toxopid.extension.configureDesktop
import com.xpdustry.toxopid.extension.configureServer
import com.xpdustry.toxopid.spec.ModMetadata
import com.xpdustry.toxopid.spec.ModPlatform
import com.xpdustry.toxopid.task.GithubAssetDownload
import com.xpdustry.toxopid.task.MindustryExec

plugins {
    id("imperium.base-conventions")
    id("imperium.publishing-conventions")
    id("com.github.johnrengelman.shadow")
    id("com.xpdustry.toxopid")
}

val metadata = ModMetadata.fromJson(project.file("plugin.json"))
metadata.minGameVersion = libs.versions.mindustry.get()
metadata.description = rootProject.description!!
metadata.version = rootProject.version.toString()

toxopid {
    compileVersion = libs.versions.mindustry.map { "v$it" }
    platforms = setOf(ModPlatform.SERVER)
}

dependencies {
    compileOnly(toxopid.dependencies.mindustryCore)
    compileOnly(toxopid.dependencies.arcCore)
    testImplementation(toxopid.dependencies.mindustryCore)
    testImplementation(toxopid.dependencies.arcCore)
    api(projects.imperiumCommon)
    implementation(libs.distributor.command.cloud)
    implementation(libs.bundles.cloud)
    compileOnly(libs.distributor.common)
    compileOnly(libs.distributor.permission.rank)
    compileOnly(libs.nohorny)
    implementation(libs.jsoup)
    implementation(libs.ahocorasick)
}

val generateResources by tasks.registering {
    inputs.property("metadata", metadata)
    outputs.files(fileTree(temporaryDir))
    doLast {
        temporaryDir.resolve("plugin.json").writeText(ModMetadata.toJson(metadata))
    }
}

tasks.shadowJar {
    archiveFileName.set("imperium-mindustry.jar")
    archiveClassifier.set("plugin")

    from(generateResources)

    from(rootProject.file("LICENSE.md")) {
        into("META-INF")
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
    exclude("org.jetbrains.kotlin")
    exclude("org.jetbrains.kotlinx")
    exclude("org.slf4j")
}

tasks.register("getArtifactPath") {
    doLast { println(tasks.shadowJar.get().archiveFile.get().toString()) }
}

tasks.build { dependsOn(tasks.shadowJar) }

val downloadKotlinRuntime =
    tasks.register<GithubAssetDownload>("downloadKotlinRuntime") {
        owner.set("xpdustry")
        repo.set("kotlin-runtime")
        asset.set("kotlin-runtime.jar")
        version.set(
            libs.versions.kotlin.runtime.zip(libs.versions.kotlin.core) { runtime, core ->
                "v$runtime-k.$core"
            },
        )
    }

val downloadSlf4md by tasks.registering(GithubAssetDownload::class) {
    owner = "xpdustry"
    repo = "slf4md"
    asset = "slf4md-simple.jar"
    version = libs.versions.slf4md.map { "v$it" }
}

val downloadDistributorCommon =
    tasks.register<GithubAssetDownload>("downloadDistributorCommon") {
        owner.set("xpdustry")
        repo.set("distributor")
        asset.set("distributor-common.jar")
        version.set(libs.versions.distributor.map { "v$it" })
    }

val downloadDistributorPermissionRank =
    tasks.register<GithubAssetDownload>("downloadDistributorPermissionRank") {
        owner.set("xpdustry")
        repo.set("distributor")
        asset.set("distributor-permission-rank.jar")
        version.set(libs.versions.distributor.map { "v$it" })
    }

val downloadNoHorny =
    tasks.register<GithubAssetDownload>("downloadNoHorny") {
        owner.set("xpdustry")
        repo.set("nohorny")
        asset.set("nohorny.jar")
        version.set(libs.versions.nohorny.map { "v$it" })
    }

tasks.register<MindustryExec>("runMindustryDesktop2") {
    group = Toxopid.TASK_GROUP_NAME
    configureDesktop()
}

tasks.runMindustryServer {
    mods.from(
        downloadKotlinRuntime,
        downloadNoHorny,
        downloadSlf4md,
        downloadDistributorCommon,
        downloadDistributorPermissionRank,
    )
}

// Second server for testing discovery
tasks.register<MindustryExec>("runMindustryServer2") {
    group = Toxopid.TASK_GROUP_NAME
    configureServer()
    mods.from(
        downloadKotlinRuntime,
        downloadNoHorny,
        downloadSlf4md,
        downloadDistributorCommon,
        downloadDistributorPermissionRank,
    )
}
