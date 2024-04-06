import java.net.URI
import java.time.Clock
import java.time.LocalDateTime
import org.gradle.accessors.dm.LibrariesForLibs
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.kotlin.dsl.the

fun RepositoryHandler.xpdustryMindustry() {
    maven {
        url = URI.create("https://maven.xpdustry.com/mindustry")
        name = "xpdustry-mindustry"
        mavenContent { releasesOnly() }
    }
}

fun RepositoryHandler.xpdustryReleases() {
    maven {
        url = URI.create("https://maven.xpdustry.com/releases")
        name = "xpdustry-releases"
        mavenContent { releasesOnly() }
    }
}

fun RepositoryHandler.xpdustrySnapshots() {
    maven {
        url = URI.create("https://maven.xpdustry.com/snapshots")
        name = "xpdustry-snapshots"
        mavenContent { snapshotsOnly() }
    }
}

val Project.libs: LibrariesForLibs get() = the()

/** Computes the next CalVer version */
fun Project.computeNextVersion(): String {
    val today = LocalDateTime.now(Clock.systemUTC())
    if (rootProject.file("VERSION.txt").exists().not()) {
        return "${today.year}.${today.monthValue}.0"
    }
    val previous = rootProject.file("VERSION.txt").readLines().first().split('.', limit = 3).map(String::toInt)
    if (previous.size != 3) {
        error("Invalid version format: ${previous.joinToString(".")}")
    }
    val build = if (today.year == previous[0] && today.monthValue == previous[1]) previous[2] + 1 else 0
    return "${today.year}.${today.monthValue}.$build"
}
