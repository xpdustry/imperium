import com.diffplug.gradle.spotless.SpotlessExtension
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import com.xpdustry.toxopid.Toxopid
import com.xpdustry.toxopid.ToxopidExtension
import com.xpdustry.toxopid.extension.configureDesktop
import com.xpdustry.toxopid.extension.configureServer
import com.xpdustry.toxopid.spec.ModDependency
import com.xpdustry.toxopid.spec.ModMetadata
import com.xpdustry.toxopid.spec.ModPlatform
import com.xpdustry.toxopid.task.GithubAssetDownload
import com.xpdustry.toxopid.task.MindustryExec
import net.kyori.indra.IndraExtension
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.JavaExec
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime

plugins {
    id("com.diffplug.spotless") version "8.6.0" apply false
    id("net.kyori.indra") version "4.0.0" apply false
    kotlin("jvm") version "2.3.21" apply false
    kotlin("plugin.serialization") version "2.3.21" apply false
    id("com.gradleup.shadow") version "9.4.2" apply false
    id("com.xpdustry.toxopid") version "4.2.0" apply false
}

group = "com.xpdustry"
description = "The core of the chaotic neutral network."
version = computeNextVersion()
val mindustryVersion = "157"

fun computeNextVersion(): String {
    val parts = rootProject.file("VERSION.txt")
        .readLines().first()
        .split('.', limit = 3).map(String::toInt)
    require(parts.size == 3) {
        "Invalid version format: $parts"
    }
    var (year, month, build) = parts

    if (findProperty("is_release").toString().toBoolean()) {
        val timestamp = ZonedDateTime.ofInstant(
            Instant.ofEpochSecond(property("build_timestamp").toString().toLong()),
            ZoneOffset.UTC)
        if (timestamp.year == year && timestamp.monthValue == month){
            build += 1
        } else {
            year = timestamp.year
            month = timestamp.monthValue
            build = 0
        }
    } else {
        build += 1
    }

    return "${year}.${month}.$build"
}

allprojects {
    group = rootProject.group
    description = rootProject.description
    version = rootProject.version
}

subprojects {
    apply(plugin = "com.diffplug.spotless")
    apply(plugin = "net.kyori.indra")
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.kotlin.plugin.serialization")

    repositories {
        mavenCentral()

        maven {
            url = uri("https://maven.xpdustry.com/mindustry")
            name = "xpdustry-mindustry"
            mavenContent { releasesOnly() }
        }

        maven {
            url = uri("https://maven.xpdustry.com/releases")
            name = "xpdustry-releases"
            mavenContent { releasesOnly() }
        }

        maven {
            url = uri("https://maven.xpdustry.com/snapshots")
            name = "xpdustry-snapshots"
            mavenContent { snapshotsOnly() }
        }
    }

    extensions.configure<IndraExtension> {
        javaVersions {
            target(25)
        }
    }

    extensions.configure<KotlinJvmProjectExtension> {
        compilerOptions {
            javaParameters = true
            freeCompilerArgs.add("-XXLanguage:+UnnamedLocalVariables")
        }
    }

    dependencies {
        "implementation"(kotlin("stdlib-jdk8"))
        "implementation"(kotlin("reflect"))
        "implementation"("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
        "implementation"("org.jetbrains.kotlinx:kotlinx-coroutines-jdk8:1.11.0")
        "testImplementation"("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
        "implementation"("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")

        "implementation"("org.slf4j:slf4j-api:2.0.18")
        "testImplementation"("org.slf4j:slf4j-simple:2.0.18")
        "testImplementation"("io.github.classgraph:classgraph:4.8.184")

        "testRuntimeOnly"("com.h2database:h2:2.4.240")
        "testRuntimeOnly"("org.mariadb.jdbc:mariadb-java-client:3.5.8")

        "testImplementation"("org.junit.jupiter:junit-jupiter-api:6.1.1")
        "testRuntimeOnly"("org.junit.jupiter:junit-jupiter-engine:6.1.1")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    extensions.configure<SpotlessExtension> {
        kotlin {
            ktfmt().kotlinlangStyle().configure { it.setMaxWidth(120) }
            licenseHeader("// SPDX-License-Identifier: GPL-3.0-only")
            trimTrailingWhitespace()
            endWithNewline()
        }
        kotlinGradle {
            ktlint().editorConfigOverride(mapOf("max_line_length" to "120"))
        }
    }
}

project(":imperium-common") {
    dependencies {
        "implementation"("org.bouncycastle:bcprov-jdk18on:1.84")

        "api"("com.google.guava:guava:33.6.0-jre")
        "api"("com.sksamuel.hoplite:hoplite-core:3.0.0.RC3")
        "api"("com.sksamuel.hoplite:hoplite-yaml:3.0.0.RC3")
        "api"("com.squareup.okhttp3:okhttp:5.3.2")
        "api"("com.github.ben-manes.caffeine:caffeine:3.2.4")

        "api"("org.jetbrains.exposed:exposed-core:1.3.1")
        "api"("org.jetbrains.exposed:exposed-jdbc:1.3.1")
        "api"("org.jetbrains.exposed:exposed-kotlin-datetime:1.3.1")
        "api"("org.jetbrains.exposed:exposed-json:1.3.1")
        "api"("com.zaxxer:HikariCP:7.0.2")

        "api"("org.ocpsoft.prettytime:prettytime:5.0.9.Final")
        "api"("net.time4j:time4j-core:4.38")
    }
}

project(":imperium-backend") {
    apply(plugin = "com.gradleup.shadow")
    apply(plugin = "com.xpdustry.toxopid")

    val toxopid = extensions.getByType<ToxopidExtension>()
    toxopid.compileVersion = "v$mindustryVersion"
    toxopid.platforms = setOf()

    dependencies {
        "implementation"(project(":imperium-common"))
        "implementation"("ch.qos.logback:logback-classic:1.5.32")
        "implementation"("net.dv8tion:JDA:6.4.1") { exclude(module = "opus-java"); exclude(module = "tink") }
        "runtimeOnly"("com.h2database:h2:2.4.240")
        "runtimeOnly"("org.mariadb.jdbc:mariadb-java-client:3.5.8")
        "implementation"(toxopid.dependencies.mindustryCore)
        "implementation"(toxopid.dependencies.arcCore)
    }

    val mindustryDesktopJar = tasks.named<GithubAssetDownload>(GithubAssetDownload.MINDUSTRY_DESKTOP_DOWNLOAD_TASK_NAME)
        .map { it.output }

    tasks.named<ProcessResources>(JavaPlugin.PROCESS_TEST_RESOURCES_TASK_NAME) {
        from(zipTree(mindustryDesktopJar)) {
            include("sprites/**")
            include("maps/**")
            include("baseparts/**")
        }
    }

    val shadowJar = tasks.named<ShadowJar>(ShadowJar.SHADOW_JAR_TASK_NAME) {
        archiveFileName.set("imperium-backend.jar")

        from(zipTree(mindustryDesktopJar)) {
            include("sprites/**")
            include("maps/**")
            include("baseparts/**")
        }

        minimize {
            exclude(dependency("org.jetbrains.kotlin:kotlin-.*:.*"))
            exclude(dependency("org.slf4j:slf4j-.*:.*"))
            exclude(dependency("ch.qos.logback:logback-.*:.*"))
            exclude(dependency("org.apache.logging.log4j:log4j-to-slf4j:.*"))
            exclude(dependency("com.sksamuel.hoplite:hoplite-.*:.*"))
            exclude(dependency("org.javacord:javacord-core:.*"))
            exclude(dependency("org.jetbrains.exposed:exposed-jdbc:.*"))
            exclude(dependency("org.mariadb.jdbc:mariadb-java-client:.*"))
            exclude(dependency("com.github.ben-manes.caffeine:caffeine:.*"))
            exclude(dependency("org.ocpsoft.prettytime:prettytime:.*"))
            exclude(dependency("net.time4j:time4j-core:.*"))
            exclude(dependency("com.h2database:h2:.*"))
        }

        manifest {
            attributes(
                "Main-Class" to "com.xpdustry.imperium.backend.ImperiumBackendKt",
                "Implementation-Title" to "ImperiumBackend",
                "Implementation-Version" to project.version.toString()
            )
        }
    }

    tasks.register<JavaExec>("runApplication") {
        workingDir = temporaryDir
        classpath(shadowJar)
    }

    tasks.named(LifecycleBasePlugin.BUILD_TASK_NAME) {
        dependsOn(tasks.named(ShadowJar.SHADOW_JAR_TASK_NAME))
    }
}

project(":imperium-mindustry") {
    apply(plugin = "com.gradleup.shadow")
    apply(plugin = "com.xpdustry.toxopid")

    val metadata = ModMetadata(
        name = "imperium",
        displayName = "Imperium",
        description = rootProject.description.toString(),
        author = "Xpdustry",
        version = rootProject.version.toString(),
        mainClass = "com.xpdustry.imperium.mindustry.ImperiumPlugin",
        repository = "xpdustry/imperium",
        minGameVersion = mindustryVersion,
        hidden = true,
        java = true,
        dependencies =
            mutableListOf(
                ModDependency("distributor-common"),
                ModDependency("kotlin-runtime"),
                ModDependency("nohorny"),
                ModDependency("sql4md-mariadb"),
                ModDependency("sql4md-h2", soft = true),
            ),
    )

    val toxopid = extensions.getByType<ToxopidExtension>()
    toxopid.compileVersion = "v$mindustryVersion"
    toxopid.platforms = setOf(ModPlatform.SERVER)

    dependencies {
        "implementation"(project(":imperium-common"))
        "compileOnly"(toxopid.dependencies.mindustryCore)
        "compileOnly"(toxopid.dependencies.arcCore)
        "testImplementation"(toxopid.dependencies.mindustryCore)
        "testImplementation"(toxopid.dependencies.arcCore)
        "implementation"("com.xpdustry:distributor-command-cloud:4.2.0")
        "implementation"("org.incendo:cloud-core:2.0.0")
        "compileOnly"("com.xpdustry:distributor-common:4.2.0")
        "compileOnly"("com.xpdustry:distributor-permission-rank:4.2.0")
        "compileOnly"("com.xpdustry:nohorny-client:4.0.0-beta.7")
        "compileOnly"("com.xpdustry:nohorny-common:4.0.0-beta.7")
        "implementation"("com.xpdustry:flex-translator:1.2.0")
        "implementation"("org.jsoup:jsoup:1.22.2")
        "implementation"("org.ahocorasick:ahocorasick:0.6.3")
    }


    val generateMetadata by tasks.registering {
        val output = layout.buildDirectory.file("imperium-generated/plugin.json")
        inputs.property("metadata", metadata)
        outputs.file(output)
        doLast { output.get().asFile.writeText(ModMetadata.toJson(metadata)) }
    }

    tasks.named<ShadowJar>(ShadowJar.SHADOW_JAR_TASK_NAME) {
        archiveFileName.set("imperium-mindustry.jar")
        archiveClassifier.set("plugin")

        from(generateMetadata)

        from(rootProject.file("LICENSE.md")) {
            into("META-INF")
        }

        mergeServiceFiles()
        minimize {
            exclude(dependency("com.sksamuel.hoplite:hoplite-.*:.*"))
            exclude(dependency("com.github.ben-manes.caffeine:caffeine:.*"))
            exclude(dependency("org.ocpsoft.prettytime:prettytime:.*"))
            exclude(dependency("net.time4j:time4j-core:.*"))
            exclude(dependency("org.jetbrains.exposed:exposed-jdbc:.*"))
        }
    }

    configurations.named(JavaPlugin.RUNTIME_CLASSPATH_CONFIGURATION_NAME) {
        exclude(group = "org.jetbrains.kotlin")
        exclude(group = "org.jetbrains.kotlinx")
        exclude(group = "org.slf4j")
    }

    val downloadKotlinRuntime = tasks.register<GithubAssetDownload>("downloadKotlinRuntime") {
        owner = "xpdustry"
        repo = "kotlin-runtime"
        asset = "kotlin-runtime.jar"
        version = "v4.3.5+k.2.3.20"
    }

    val downloadSlf4md = tasks.register<GithubAssetDownload>("downloadSlf4md") {
        owner = "xpdustry"
        repo = "slf4md"
        asset = "slf4md.jar"
        version = "v1.2.1"
    }

    val downloadSql4mdMariadb = tasks.register<GithubAssetDownload>("downloadSql4mdMariadb") {
        owner = "xpdustry"
        repo = "sql4md"
        asset = "sql4md-mariadb.jar"
        version = "v2.0.2"
    }

    val downloadSql4mdH2 = tasks.register<GithubAssetDownload>("downloadSql4mdH2") {
        owner = "xpdustry"
        repo = "sql4md"
        asset = "sql4md-h2.jar"
        version = "v2.0.2"
    }

    val downloadDistributorCommon = tasks.register<GithubAssetDownload>("downloadDistributorCommon") {
        owner = "xpdustry"
        repo = "distributor"
        asset = "distributor-common.jar"
        version = "v4.2.0"
    }

    val downloadDistributorPermissionRank = tasks.register<GithubAssetDownload>("downloadDistributorPermissionRank") {
        owner = "xpdustry"
        repo = "distributor"
        asset = "distributor-permission-rank.jar"
        version = "v4.2.0"
    }

    val downloadNoHorny = tasks.register<GithubAssetDownload>("downloadNoHorny") {
        owner = "xpdustry"
        repo = "nohorny"
        asset = "nohorny-client.jar"
        version = "v4.0.0-beta.6"
    }

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

    tasks.named<MindustryExec>(MindustryExec.SERVER_EXEC_TASK_NAME) {
        mods.from(pluginDependencies)
    }

    tasks.register<MindustryExec>("runMindustryServer2") {
        group = Toxopid.TASK_GROUP_NAME
        configureServer()
        mods.from(tasks.named(ShadowJar.SHADOW_JAR_TASK_NAME), pluginDependencies)
    }

    tasks.named(LifecycleBasePlugin.BUILD_TASK_NAME) {
        dependsOn(tasks.named(ShadowJar.SHADOW_JAR_TASK_NAME))
    }
}
