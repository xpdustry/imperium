import com.diffplug.gradle.spotless.FormatExtension
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.RepositoryHandler
import java.net.URI
import java.time.Clock
import java.time.LocalDateTime

fun Project.getCalverVersion(): String {
    val today = LocalDateTime.now(Clock.systemUTC())
    val file = project.file("VERSION.txt")
    if (file.exists().not()) {
        return "${today.year}.${today.monthValue}.0"
    }
    val previous = file.readLines().first().split('.').map(String::toInt)
    val build = if (today.year == previous[0] && today.monthValue == previous[1]) previous[2] + 1 else 0
    return "${today.year}.${today.monthValue}.$build"
}

// https://github.com/Incendo/cloud/blob/a4cc749b91564af57bb7bba36dd8011b556c2b3a/build-logic/src/main/kotlin/cloud.base-conventions.gradle.kts#L43
fun FormatExtension.applyCommon() {
    indentWithSpaces(4)
    trimTrailingWhitespace()
    endWithNewline()
}

fun RepositoryHandler.xpdustryReleases() = maven {
    url = URI.create("https://maven.xpdustry.fr/releases")
    name = "xpdustry-releases"
    mavenContent { releasesOnly() }
}
