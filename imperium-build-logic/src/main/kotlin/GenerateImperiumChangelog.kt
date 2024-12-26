import java.util.regex.Pattern
import net.kyori.indra.git.IndraGitExtension
import org.eclipse.jgit.lib.Constants
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.getByType

@CacheableTask
open class GenerateImperiumChangelog : DefaultTask() {

    @OutputFile
    val target: RegularFileProperty = project.objects.fileProperty()

    @TaskAction
    fun generate() {
        val git = project.rootProject.extensions.getByType<IndraGitExtension>().git()!!
        val latest = git.repository.resolve("v" + project.rootProject.file("VERSION.txt").readText().trim())
        val head = git.repository.resolve(Constants.HEAD)
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
        private val ACCEPTED_SUFFIX = Pattern.compile("^(?<verb>feat|fix)(\\((?<scope>mindustry|discord)\\))?:", Pattern.CASE_INSENSITIVE)
    }
}