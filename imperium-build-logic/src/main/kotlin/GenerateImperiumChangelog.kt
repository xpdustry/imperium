import java.util.regex.Pattern
import net.kyori.indra.git.IndraGitExtension
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.Constants
import org.eclipse.jgit.transport.URIish
import org.gradle.api.DefaultTask
import org.gradle.api.Task
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.getByType

open class GenerateImperiumChangelog : DefaultTask() {

    @OutputFile
    val target: RegularFileProperty = project.objects.fileProperty()

    fun onlyIfHasUpstream() {
        onlyIf("run only if upstream repo is available") { task ->
            task.git.remoteList().call().any { remote -> remote.urIs.contains(UPSTREAM_URI_1) || remote.urIs.contains(UPSTREAM_URI_2) }
        }
    }

    @TaskAction
    fun generate() {
        val latest = git.repository.resolve("v" + project.rootProject.file("VERSION.txt").readText().trim())
            ?: error("The version in VERSION.txt is not available")
        val head = git.repository.resolve(Constants.HEAD)!!
        target.get().asFile.writer().buffered().use { writer ->
            git.log().addRange(latest, head).call().forEach { commit ->
                val message = commit.shortMessage ?: return@forEach
                val matcher = ACCEPTED_SUFFIX.matcher(message)
                if (!matcher.find()) return@forEach
                writer.write(matcher.group("verb").lowercase())
                writer.write("/")
                writer.write(matcher.group("scope")?.lowercase() ?: "common")
                writer.write("/")
                writer.write(message.split(':', limit = 2)[1].trim())
                writer.newLine()
            }
        }
    }

    companion object {
        private val ACCEPTED_SUFFIX =
            Pattern.compile("^(?<verb>feat|fix)(\\((?<scope>mindustry|discord)\\))?:", Pattern.CASE_INSENSITIVE)
        private val UPSTREAM_URI_1 = URIish("https://github.com/xpdustry/imperium.git")
        private val UPSTREAM_URI_2 = URIish("https://github.com/xpdustry/imperium")
        private val Task.git: Git
            get() = project.rootProject.extensions.getByType<IndraGitExtension>().git()!!
    }
}