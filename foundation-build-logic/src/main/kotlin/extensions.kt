import com.diffplug.gradle.spotless.FormatExtension
import org.gradle.api.artifacts.dsl.RepositoryHandler
import java.net.URI

// https://github.com/Incendo/cloud/blob/a4cc749b91564af57bb7bba36dd8011b556c2b3a/build-logic/src/main/kotlin/cloud.base-conventions.gradle.kts#L43
fun FormatExtension.applyCommon() {
    indentWithSpaces(4)
    trimTrailingWhitespace()
    endWithNewline()
}

fun RepositoryHandler.xpdustryReleases() {
    maven {
        url = URI.create("https://maven.xpdustry.fr/releases")
        name = "xpdustry-releases"
        mavenContent { releasesOnly() }
    }
}
