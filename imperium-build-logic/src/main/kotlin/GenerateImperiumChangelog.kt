import net.kyori.indra.git.internal.IndraGitService
import org.eclipse.jgit.lib.Constants
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.services.ServiceReference
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.util.regex.Pattern

abstract class GenerateImperiumChangelog : DefaultTask() {

    @get:OutputFile
    abstract val target: RegularFileProperty

    @get:ServiceReference(IndraGitService.SERVICE_NAME)
    abstract val gitService: Property<IndraGitService>

    @get:Internal
    abstract val projectDirectory: DirectoryProperty

    @get:Internal
    abstract val projectDisplayName: Property<String>

    init {
        projectDirectory.fileValue(project.projectDir)
        projectDisplayName.convention(project.displayName)
    }

    fun onlyIfHasUpstream() {
        onlyIf("run only if upstream repo is available") { task ->
            return@onlyIf task.project.hasProperty("generateChangelog")
                    && task.project.property("generateChangelog").toString().toBoolean()
        }
    }

    @TaskAction
    fun generate() {
        val git = gitService.get().git(projectDirectory.get().asFile, projectDisplayName.get())!!
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
    }
}