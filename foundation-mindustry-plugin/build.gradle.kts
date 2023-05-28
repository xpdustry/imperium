import fr.xpdustry.toxopid.dsl.anukenJitpack
import fr.xpdustry.toxopid.dsl.mindustryDependencies
import fr.xpdustry.toxopid.task.GithubArtifactDownload

plugins {
    id("foundation.kotlin-conventions")
    id("foundation.publishing-conventions")
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
    useMindustryMirror.set(true)
}

repositories {
    anukenJitpack()
    xpdustryReleases()
}

dependencies {
    api(projects.foundationCommon)
    api(projects.foundationMindustryLibrary)
    mindustryDependencies()
    compileOnly(libs.distributor.api)
    compileOnly(libs.javelin.mindustry)
    compileOnly(kotlin("stdlib"))
}

tasks.shadowJar {
    archiveFileName.set("FoundationMindustry.jar")
    archiveClassifier.set("plugin")

    doFirst {
        val temp = temporaryDir.resolve("plugin.json")
        temp.writeText(metadata.toJson(true))
        from(temp)
    }

    from(rootProject.file("LICENSE.md")) {
        into("META-INF")
    }

    fun relocatePackage(source: String, target: String = source.split(".").last()) =
        relocate(source, "com.xpdustry.foundation.shadow.$target")

    minimize()
    mergeServiceFiles()
}

tasks.register("getArtifactPath") {
    doLast { println(tasks.shadowJar.get().archiveFile.get().toString()) }
}

tasks.build {
    dependsOn(tasks.shadowJar)
}

val downloadKotlinRuntime = tasks.register<GithubArtifactDownload>("downloadKotlinRuntime") {
    user.set("Xpdustry")
    repo.set("KotlinRuntimePlugin")
    name.set("KotlinRuntimePlugin.jar")
    version.set(libs.versions.kotlin.map { "v2.0.0-k.$it" })
}

val downloadJavelin = tasks.register<GithubArtifactDownload>("downloadJavelin") {
    user.set("Xpdustry")
    repo.set("Javelin")
    name.set("Javelin.jar")
    version.set(libs.versions.javelin.map { "v$it" })
}

val downloadDistributor = tasks.register<GithubArtifactDownload>("downloadDistributor") {
    user.set("Xpdustry")
    repo.set("Distributor")
    name.set("DistributorCore.jar")
    version.set(libs.versions.distributor.map { "v$it" })
}

tasks.runMindustryClient {
    mods.setFrom()
}

tasks.runMindustryServer {
    mods.setFrom(downloadKotlinRuntime, tasks.shadowJar, downloadJavelin, downloadDistributor)
}
