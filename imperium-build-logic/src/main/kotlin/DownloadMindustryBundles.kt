import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.nio.file.Files
import java.util.Properties
import java.util.zip.ZipInputStream
import kotlin.io.path.bufferedReader
import kotlin.io.path.bufferedWriter
import kotlin.io.path.moveTo
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

@CacheableTask
open class DownloadMindustryBundles : DefaultTask() {

    @get:OutputDirectory
    val output: DirectoryProperty = project.objects.directoryProperty()

    init {
        output.convention(project.layout.dir(project.provider { temporaryDir }))
    }

    @TaskAction
    fun download() {
        val uri = URI.create("https://github.com/Anuken/Mindustry/archive/refs/heads/master.zip")

        val response =
            HTTP.send(HttpRequest.newBuilder(uri).GET().build(), BodyHandlers.ofInputStream())

        if (response.statusCode() != 200) {
            throw RuntimeException("Failed to download $uri")
        }

        val directory = output.get().asFile.toPath()
        ZipInputStream(response.body()).use { zip ->
            var entry = zip.getNextEntry()
            while (entry != null) {
                if (entry.name.contains("/core/assets/bundles/") && !entry.isDirectory) {
                    val file =
                        output.get().asFile.toPath().resolve(entry.name.split("/").last())
                    Files.createDirectories(file.parent)
                    Files.newOutputStream(file).use { output ->
                        // https://stackoverflow.com/a/22646404
                        val buffer = ByteArray(1024)
                        var bytesRead: Int
                        while (zip.read(buffer).also { bytesRead = it } != -1 &&
                            bytesRead <= entry!!.size) {
                            output.write(buffer, 0, bytesRead)
                        }
                    }
                }
                zip.closeEntry()
                entry = zip.getNextEntry()
            }
        }

        Files.list(directory).forEach { entry ->
            val properties = Properties()
            entry.bufferedReader().use(properties::load)
            val filtered = properties.filterKeys { key -> (key as String).matches(CONTENT_NAME_REGEX) }
            entry.bufferedWriter().use {
                Properties().apply { putAll(filtered) }.store(it, null)
            }
        }

        directory.resolve("bundle.properties").moveTo(directory.resolve("bundle_en.properties"))
    }

    companion object {
        private val CONTENT_NAME_REGEX = Regex("^(block|item|liquid|planet)\\.(.+)\\.name$")
        private val HTTP = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build()
    }
}