import com.xpdustry.toxopid.Toxopid
import com.xpdustry.toxopid.extension.configureDesktop
import com.xpdustry.toxopid.extension.configureServer
import com.xpdustry.toxopid.spec.ModMetadata
import com.xpdustry.toxopid.spec.ModPlatform
import com.xpdustry.toxopid.task.GithubAssetDownload
import com.xpdustry.toxopid.task.MindustryExec

plugins {
    id("imperium.base-conventions")
    id("com.gradleup.shadow")
    id("com.xpdustry.toxopid")
}

val metadata = ModMetadata.fromJson(rootProject.file("plugin.json"))
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
    compileOnly(libs.nohorny.common)
    implementation(libs.flex.translator)
    implementation(libs.jsoup)
    implementation(libs.ahocorasick)
}

val generateMetadata by tasks.registering {
    inputs.property("metadata", metadata)
    outputs.files(fileTree(temporaryDir))
    doLast {
        temporaryDir.resolve("plugin.json").writeText(ModMetadata.toJson(metadata))
    }
}

val generateChangelog by tasks.registering(GenerateImperiumChangelog::class) {
    outputs.upToDateWhen { false }
    onlyIfHasUpstream()
    target = temporaryDir.resolve("imperium-changelog.txt")
}

tasks.shadowJar {
    archiveFileName.set("imperium-mindustry.jar")
    archiveClassifier.set("plugin")

    from(generateMetadata, generateChangelog)

    from(rootProject.file("LICENSE.md")) {
        into("META-INF")
    }

    mergeServiceFiles()
    minimize {
        exclude(dependency("com.sksamuel.hoplite:hoplite-.*:.*"))
        exclude(dependency(libs.caffeine.get()))
        exclude(dependency(libs.prettytime.get()))
        exclude(dependency(libs.time4j.core.get()))
        exclude(dependency(libs.exposed.jdbc.get()))
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

val downloadKotlinRuntime by tasks.registering(GithubAssetDownload::class) {
    owner = "xpdustry"
    repo = "kotlin-runtime"
    asset = "kotlin-runtime.jar"
    version = libs.versions.kotlin.runtime.zip(libs.versions.kotlin.core) { runtime, core ->
        "v$runtime+k.$core"
    }
}

val downloadSlf4md by tasks.registering(GithubAssetDownload::class) {
    owner = "xpdustry"
    repo = "slf4md"
    asset = "slf4md.jar"
    version = libs.versions.slf4md.map { "v$it" }
}

val downloadSql4mdMariadb by tasks.registering(GithubAssetDownload::class) {
    owner = "xpdustry"
    repo = "sql4md"
    asset = "sql4md-mariadb.jar"
    version = libs.versions.sql4md.map { "v$it" }
}

val downloadSql4mdH2 by tasks.registering(GithubAssetDownload::class) {
    owner = "xpdustry"
    repo = "sql4md"
    asset = "sql4md-h2.jar"
    version = libs.versions.sql4md.map { "v$it" }
}

val downloadDistributorCommon by tasks.registering(GithubAssetDownload::class) {
    owner = "xpdustry"
    repo = "distributor"
    asset = "distributor-common.jar"
    version = libs.versions.distributor.map { "v$it" }
}

val downloadDistributorPermissionRank by tasks.registering(GithubAssetDownload::class) {
    owner = "xpdustry"
    repo = "distributor"
    asset = "distributor-permission-rank.jar"
    version = libs.versions.distributor.map { "v$it" }
}

val downloadNoHorny by tasks.registering(GithubAssetDownload::class) {
    owner = "xpdustry"
    repo = "nohorny"
    asset = "nohorny-client.jar"
    version = libs.versions.nohorny.map { "v$it" }
}

// Second client for testing locally
tasks.register<MindustryExec>("runMindustryDesktop2") {
    group = Toxopid.TASK_GROUP_NAME
    configureDesktop()
}

val pluginDependencies = listOf(
    downloadKotlinRuntime,
    downloadNoHorny,
    downloadSlf4md,
    downloadSql4mdMariadb,
    downloadSql4mdH2,
    downloadDistributorCommon,
    downloadDistributorPermissionRank,
)

tasks.runMindustryServer {
    mods.from(pluginDependencies)
}

// Second server for testing discovery
tasks.register<MindustryExec>("runMindustryServer2") {
    group = Toxopid.TASK_GROUP_NAME
    configureServer()
    mods.from(tasks.shadowJar, pluginDependencies)
}
