import com.github.jengelman.gradle.plugins.shadow.internal.RelocationUtil
import com.github.jengelman.gradle.plugins.shadow.relocation.SimpleRelocator
import fr.xpdustry.toxopid.dsl.mindustryDependencies
import fr.xpdustry.toxopid.task.GithubArtifactDownload
import fr.xpdustry.toxopid.task.MindustryExec

plugins {
    id("foundation.base-conventions")
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
    // anukenJitpack() Jitpack is terribly unreliable, so we use our own mirror
    xpdustryAnuken()
    xpdustryReleases()
}

dependencies {
    api(projects.foundationCommon) {
        exclude("org.jetbrains.kotlin", "kotlin-stdlib")
        exclude("org.jetbrains.kotlin", "kotlin-stdlib-jdk8")
        exclude("org.jetbrains.kotlin", "kotlin-reflect")
        exclude("org.slf4j")
    }
    implementation(libs.jsoup)
    mindustryDependencies()
    compileOnly(libs.distributor.api)
    compileOnly(libs.distributor.kotlin)

    testImplementation("org.testcontainers:testcontainers:1.18.3")
    testImplementation("org.testcontainers:mongodb:1.18.3")
    testImplementation("org.testcontainers:rabbitmq:1.18.3")
    testImplementation("org.testcontainers:junit-jupiter:1.18.3")
    testImplementation(libs.reactor.test)
    testImplementation(kotlin("stdlib"))
    testImplementation(kotlin("reflect"))
    testImplementation(libs.distributor.api)
}

tasks.shadowJar {
    archiveFileName.set("FoundationMindustry.jar")
    archiveClassifier.set("plugin")

    doFirst {
        @Suppress("SpellCheckingInspection")
        fun removeRelocator(pattern: String) =
            relocators.removeAll { it is SimpleRelocator && it.pattern.startsWith(pattern) }

        RelocationUtil.configureRelocation(this@shadowJar, "com.xpdustry.foundation.shadow")
        removeRelocator("com.xpdustry.foundation.common")
    }

    doFirst {
        val temp = temporaryDir.resolve("plugin.json")
        temp.writeText(metadata.toJson(true))
        from(temp)
    }

    from(rootProject.file("LICENSE.md")) {
        into("META-INF")
    }

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

val downloadDistributorCore = tasks.register<GithubArtifactDownload>("downloadDistributor") {
    user.set("Xpdustry")
    repo.set("Distributor")
    name.set("DistributorCore.jar")
    version.set(libs.versions.distributor.map { "v$it" })
}

val downloadDistributorKotlin = tasks.register<GithubArtifactDownload>("downloadDistributorKotlin") {
    user.set("Xpdustry")
    repo.set("Distributor")
    name.set("DistributorKotlin.jar")
    version.set(libs.versions.distributor.map { "v$it" })
}

tasks.runMindustryClient {
    mods.setFrom()
}

tasks.register<MindustryExec>("runMindustryClient2") {
    group = fr.xpdustry.toxopid.Toxopid.TASK_GROUP_NAME
    classpath(tasks.downloadMindustryClient)
    mainClass.convention("mindustry.desktop.DesktopLauncher")
    modsPath.convention("./mods")
    standardInput = System.`in`
}

tasks.runMindustryServer {
    mods.setFrom(downloadKotlinRuntime, tasks.shadowJar, downloadDistributorCore, downloadDistributorKotlin)
}

// Second server for testing discovery
tasks.register<MindustryExec>("runMindustryServer2") {
    group = fr.xpdustry.toxopid.Toxopid.TASK_GROUP_NAME
    classpath(tasks.downloadMindustryServer)
    mainClass.set("mindustry.server.ServerLauncher")
    modsPath.set("./config/mods")
    standardInput = System.`in`
    mods.setFrom(downloadKotlinRuntime, tasks.shadowJar, downloadDistributorCore, downloadDistributorKotlin)
}
