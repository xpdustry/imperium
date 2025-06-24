/*
 * Imperium, the software collection powering the Chaotic Neutral network.
 * Copyright (C) 2024  Xpdustry
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.xpdustry.imperium.discord.content

import arc.Core
import arc.files.Fi
import arc.graphics.Color
import arc.graphics.Texture
import arc.graphics.g2d.Draw
import arc.graphics.g2d.Lines
import arc.graphics.g2d.SpriteBatch
import arc.graphics.g2d.TextureAtlas
import arc.graphics.g2d.TextureAtlas.AtlasRegion
import arc.graphics.g2d.TextureAtlas.TextureAtlasData
import arc.graphics.g2d.TextureRegion
import arc.math.Mathf
import arc.math.geom.Point2
import arc.mock.MockSettings
import arc.struct.IntMap
import arc.struct.OrderedSet
import arc.struct.Seq
import arc.struct.StringMap
import arc.util.Log
import arc.util.io.CounterInputStream
import arc.util.io.Reads
import arc.util.io.Writes
import arc.util.serialization.Base64Coder
import com.google.common.cache.CacheBuilder
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.lifecycle.LifecycleListener
import com.xpdustry.imperium.common.misc.LoggerDelegate
import jakarta.inject.Inject
import jakarta.inject.Named
import java.awt.Graphics2D
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream
import java.util.zip.ZipInputStream
import javax.imageio.ImageIO
import kotlin.io.path.extension
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import mindustry.Vars
import mindustry.content.Blocks
import mindustry.core.ContentLoader
import mindustry.core.GameState
import mindustry.core.Version
import mindustry.core.World
import mindustry.ctype.ContentType
import mindustry.entities.units.BuildPlan
import mindustry.game.Schematic
import mindustry.game.Team
import mindustry.io.JsonIO
import mindustry.io.MapIO
import mindustry.io.SaveFileReader
import mindustry.io.SaveIO
import mindustry.io.TypeIO
import mindustry.world.Block
import mindustry.world.CachedTile
import mindustry.world.Tile
import mindustry.world.WorldContext
import mindustry.world.blocks.environment.OreBlock
import mindustry.world.blocks.legacy.LegacyBlock

// The base code is from Anuken/CoreBot, rewritten in kotlin and modified to be able to run in a
// multithreaded environment.
class AnukenMindustryContentHandler
@Inject
constructor(@Named("directory") directory: Path, private val config: ImperiumConfig) :
    MindustryContentHandler, LifecycleListener {
    private var currentSchematicGraphics: Graphics2D? = null
    private var currentSchematicImage: BufferedImage? = null

    private val directory = directory.resolve("mindustry-assets")
    private val images = mutableMapOf<String, Path>()
    private val regions =
        CacheBuilder.newBuilder().expireAfterAccess(Duration.of(1L, ChronoUnit.MINUTES)).build<String, BufferedImage>()

    override fun onImperiumInit() {
        Version.enabled = false
        Vars.headless = true
        Core.settings = MockSettings()

        Log.logger = Log.NoopLogHandler()
        Log.logger = Log.DefaultLogHandler()

        Vars.content = ContentLoader()
        Vars.content.createBaseContent()
        Vars.content.init()

        downloadAssets()

        Vars.state = GameState()
        Core.atlas = TextureAtlas()

        val data =
            TextureAtlasData(
                Fi(directory.resolve("sprites/sprites.aatls").toFile()),
                Fi(directory.resolve("sprites").toFile()),
                false,
            )

        Files.walk(directory.resolve("raw-sprites"))
            .filter { it.extension == "png" }
            .forEach { images[it.nameWithoutExtension] = it }

        Files.walk(directory.resolve("generated"))
            .filter { it.extension == "png" }
            .forEach { images[it.nameWithoutExtension] = it }

        data.pages.forEach {
            it.texture = Texture.createEmpty(null)
            it.texture.width = it.width
            it.texture.height = it.height
        }

        data.regions.forEach {
            Core.atlas.addRegion(
                it.name,
                AtlasRegion(it.page.texture, it.left, it.top, it.width, it.height).apply {
                    name = it.name
                    texture = it.page.texture
                },
            )
        }

        Lines.useLegacyLine = true
        Core.atlas.setErrorRegion("error")
        Draw.scl = 1f / 4f
        Core.batch =
            object : SpriteBatch(0) {
                override fun draw(
                    region: TextureRegion,
                    x: Float,
                    y: Float,
                    originX: Float,
                    originY: Float,
                    w: Float,
                    h: Float,
                    rotation: Float,
                ) {
                    var sx = x
                    var sy = y
                    var sw = w
                    var sh = h
                    sx += 4f
                    sy += 4f
                    sx *= 4f
                    sy *= 4f
                    sw *= 4f
                    sh *= 4f
                    sy = currentSchematicImage!!.height - (sy + sh / 2f) - sh / 2f
                    val affine = AffineTransform()
                    affine.translate(sx.toDouble(), sy.toDouble())
                    affine.rotate(
                        (-rotation * Mathf.degRad).toDouble(),
                        (originX * 4).toDouble(),
                        (originY * 4).toDouble(),
                    )
                    currentSchematicGraphics!!.transform = affine
                    var image = getImage((region as AtlasRegion).name)
                    if (color != Color.white) {
                        image = tint(image, color)
                    }
                    currentSchematicGraphics!!.drawImage(image, 0, 0, sw.toInt(), sh.toInt(), null)
                }

                // Do nothing
                override fun draw(texture: Texture, spriteVertices: FloatArray, offset: Int, count: Int) = Unit
            }

        Vars.content.load()

        val image = ImageIO.read(directory.resolve("sprites/block_colors.png").toFile())
        for (block in Vars.content.blocks()) {
            block.mapColor.argb8888(image.getRGB(block.id.toInt(), 0))
            (block as? OreBlock)?.mapColor?.set(block.itemDrop.color)
        }

        Vars.world =
            object : World() {
                override fun tile(x: Int, y: Int): Tile = Tile(x, y)
            }
    }

    private fun downloadAssets() {
        val versionFile = directory.resolve("VERSION.txt")
        if (Files.exists(versionFile) && versionFile.readText() == config.discord.mindustryVersion) {
            return
        }

        if (Files.exists(directory)) {
            Files.walk(directory).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete)
        }
        Files.createDirectories(directory)

        val http =
            HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.ofSeconds(10L))
                .build()

        downloadZipDirectory(
            http,
            URI.create(
                "https://github.com/Anuken/Mindustry/releases/download/v${config.discord.mindustryVersion}/Mindustry.jar"
            ),
            "sprites",
            "sprites",
        )

        downloadZipDirectory(
            http,
            URI.create("https://github.com/Anuken/Mindustry/archive/refs/tags/v${config.discord.mindustryVersion}.zip"),
            "Mindustry-${config.discord.mindustryVersion}/core/assets-raw/sprites",
            "raw-sprites",
        )

        MindustryImagePacker(directory).pack()

        versionFile.writeText(config.discord.mindustryVersion)
    }

    private fun downloadZipDirectory(http: HttpClient, uri: URI, folder: String, destination: String) {
        logger.info("Downloading assets from $uri")

        val response = http.send(HttpRequest.newBuilder(uri).GET().build(), BodyHandlers.ofInputStream())

        if (response.statusCode() != 200) {
            throw RuntimeException("Failed to download $uri")
        }

        ZipInputStream(response.body()).use { zip ->
            var entry = zip.getNextEntry()
            while (entry != null) {
                if (entry.name.startsWith(folder) && !entry.isDirectory) {
                    val file = directory.resolve(destination).resolve(entry.name.substring(folder.length + 1))
                    Files.createDirectories(file.parent)
                    Files.newOutputStream(file).use { output ->
                        // https://stackoverflow.com/a/22646404
                        val buffer = ByteArray(1024)
                        var bytesRead: Int
                        while (zip.read(buffer).also { bytesRead = it } != -1 && bytesRead <= entry!!.size) {
                            output.write(buffer, 0, bytesRead)
                        }
                    }
                }
                zip.closeEntry()
                entry = zip.getNextEntry()
            }
        }
    }

    override suspend fun getSchematic(stream: InputStream): Result<Schematic> =
        withContext(ImperiumScope.IO.coroutineContext) { getSchematic0(stream) }

    override suspend fun getSchematic(string: String): Result<Schematic> =
        withContext(ImperiumScope.IO.coroutineContext) {
            getSchematic0(
                try {
                    ByteArrayInputStream(Base64Coder.decode(string))
                } catch (e: Exception) {
                    return@withContext Result.failure(e)
                }
            )
        }

    private fun getSchematic0(stream: InputStream): Result<Schematic> = runCatching {
        for (b in SCHEMATIC_HEADER) {
            if (stream.read() != b.toInt()) {
                throw IOException("Not a schematic file (missing header).")
            }
        }

        if (stream.read() != 1) {
            throw IOException("Unsupported schematic version.")
        }

        DataInputStream(InflaterInputStream(stream)).use { data ->
            val width = data.readShort()
            val height = data.readShort()
            val map = StringMap()
            val tags = data.readByte()
            for (i in 0 until tags) {
                map.put(data.readUTF(), data.readUTF())
            }

            val blocks = IntMap<Block>()
            val length = data.readByte()
            for (i in 0 until length) {
                val name = data.readUTF()
                val block = Vars.content.getByName<Block>(ContentType.block, SaveFileReader.fallback[name, name])
                blocks.put(i, if (block == null || block is LegacyBlock) Blocks.air else block)
            }

            val total = data.readInt()
            if (total > MAX_SCHEMATIC_SIDE_SIZE * MAX_SCHEMATIC_SIDE_SIZE)
                throw IOException("Schematic has too many blocks.")
            val tiles = Seq<Schematic.Stile>(total)
            for (i in 0 until total) {
                val block = blocks[data.readByte().toInt()]
                val position = data.readInt()
                val config = TypeIO.readObject(Reads.get(data))
                val rotation = data.readByte()
                if (block !== Blocks.air) {
                    tiles.add(
                        Schematic.Stile(block, Point2.x(position).toInt(), Point2.y(position).toInt(), config, rotation)
                    )
                }
            }

            Schematic(tiles, map, width.toInt(), height.toInt())
        }
    }

    override suspend fun getSchematicPreview(schematic: Schematic): Result<BufferedImage> =
        withContext(SCHEMATIC_PREVIEW_SCOPE.coroutineContext) {
            runCatching {
                if (schematic.width > MAX_SCHEMATIC_SIDE_SIZE || schematic.height > MAX_SCHEMATIC_SIDE_SIZE) {
                    throw IOException(
                        "Schematic cannot be larger than $MAX_SCHEMATIC_SIDE_SIZE x $MAX_SCHEMATIC_SIDE_SIZE."
                    )
                }

                val image = BufferedImage(schematic.width * 32, schematic.height * 32, BufferedImage.TYPE_INT_ARGB)

                Draw.reset()
                val requests =
                    schematic.tiles.map {
                        BuildPlan(it.x.toInt(), it.y.toInt(), it.rotation.toInt(), it.block, it.config)
                    }

                currentSchematicGraphics = image.createGraphics()
                currentSchematicImage = image

                requests.each {
                    it.animScale = 1f
                    it.worldContext = false
                    it.block.drawPlanRegion(it, requests)
                    Draw.reset()
                    it.block.drawPlanConfigTop(it, requests)
                }

                image
            }
        }

    override suspend fun writeSchematic(schematic: Schematic, output: OutputStream): Result<Unit> =
        withContext(ImperiumScope.IO.coroutineContext) {
            runCatching {
                output.write(SCHEMATIC_HEADER)
                output.write(1)

                DataOutputStream(DeflaterOutputStream(output)).use { stream ->
                    stream.writeShort(schematic.width)
                    stream.writeShort(schematic.height)
                    val tags = StringMap().apply { putAll(schematic.tags) }
                    tags.put("labels", JsonIO.write(schematic.labels.toArray<Any>(String::class.java)))
                    stream.writeByte(tags.size)
                    for (e in tags.entries()) {
                        stream.writeUTF(e.key)
                        stream.writeUTF(e.value)
                    }
                    val blocks = OrderedSet<Block>()
                    schematic.tiles.forEach { blocks.add(it.block) }

                    // create dictionary
                    stream.writeByte(blocks.size)
                    for (i in 0 until blocks.size) {
                        stream.writeUTF(blocks.orderedItems()[i].name)
                    }
                    stream.writeInt(schematic.tiles.size)
                    // write each tile
                    for (tile in schematic.tiles) {
                        stream.writeByte(blocks.orderedItems().indexOf(tile.block))
                        stream.writeInt(Point2.pack(tile.x.toInt(), tile.y.toInt()))
                        TypeIO.writeObject(Writes.get(stream), tile.config)
                        stream.writeByte(tile.rotation.toInt())
                    }
                }
            }
        }

    override suspend fun getMapMetadata(stream: InputStream): Result<MapMetadata> =
        withContext(ImperiumScope.IO.coroutineContext) { getMapMetadataWithPreview0(stream, false).map { it.first } }

    override suspend fun getMapMetadataWithPreview(stream: InputStream): Result<Pair<MapMetadata, BufferedImage>> =
        withContext(MAP_PREVIEW_SCOPE.coroutineContext) {
            getMapMetadataWithPreview0(stream, true).map { it.first to it.second!! }
        }

    private fun getMapMetadataWithPreview0(
        input: InputStream,
        preview: Boolean,
    ): Result<Pair<MapMetadata, BufferedImage?>> = runCatching {
        val counter = CounterInputStream(InflaterInputStream(input))
        val stream = DataInputStream(counter)
        SaveIO.readHeader(stream)

        val version = stream.readInt()
        val ver = SaveIO.getSaveWriter(version)
        val metaOut = arrayOf<StringMap?>(null)

        ver.region("meta", stream, counter) { metaOut[0] = ver.readStringMap(it) }

        val meta = metaOut[0]!!
        val name = meta["name"] ?: throw IOException("Map does not have a name.")
        val author = meta["author"]?.takeUnless(String::isBlank)
        val description = meta["description"]?.takeUnless(String::isBlank)
        val width = meta.getInt("width")
        val height = meta.getInt("height")
        var floors: BufferedImage? = null

        if (preview) {
            try {
                ver.region("content", stream, counter) { ver.readContentHeader(it) }
                floors = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
                val walls = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
                val fGraphics = floors.createGraphics()
                val jColor = java.awt.Color(0, 0, 0, 64)
                val black = 255
                val tile: CachedTile =
                    object : CachedTile() {
                        override fun setBlock(type: Block) {
                            super.setBlock(type)
                            val c = MapIO.colorFor(block(), Blocks.air, Blocks.air, team())
                            if (c != black && c != 0) {
                                walls.setRGB(x.toInt(), floors.height - 1 - y, conv(c))
                                fGraphics.color = jColor
                                fGraphics.drawRect(x.toInt(), floors.height - 1 - y + 1, 1, 1)
                            }
                        }
                    }
                ver.region("preview_map", stream, counter) {
                    ver.readMap(
                        it,
                        object : WorldContext {
                            override fun resize(width: Int, height: Int) = Unit

                            override fun isGenerating(): Boolean = false

                            override fun begin() {
                                Vars.world.isGenerating = true
                            }

                            override fun end() {
                                Vars.world.isGenerating = false
                            }

                            override fun onReadBuilding() {
                                // read team colors
                                if (tile.build != null) {
                                    val c = tile.build.team.color.argb8888()
                                    val size = tile.block().size
                                    val offsetx = -(size - 1) / 2
                                    val offsety = -(size - 1) / 2
                                    for (dx in 0 until size) {
                                        for (dy in 0 until size) {
                                            val drawx = tile.x + dx + offsetx
                                            val drawy = tile.y + dy + offsety
                                            walls.setRGB(drawx, floors.height - 1 - drawy, c)
                                        }
                                    }
                                }
                            }

                            override fun tile(index: Int): Tile {
                                tile.x = (index % width).toShort()
                                tile.y = (index / width).toShort()
                                return tile
                            }

                            override fun create(x: Int, y: Int, floorID: Int, overlayID: Int, wallID: Int): Tile {
                                floors.setRGB(
                                    x,
                                    floors.height - 1 - y,
                                    conv(
                                        MapIO.colorFor(
                                            Blocks.air,
                                            if (overlayID != 0) Blocks.air else Vars.content.block(floorID),
                                            if (overlayID != 0) Vars.content.block(overlayID) else Blocks.air,
                                            Team.derelict,
                                        )
                                    ),
                                )
                                return tile
                            }
                        },
                    )
                }
                fGraphics.drawImage(walls, 0, 0, null)
                fGraphics.dispose()
            } finally {
                Vars.content.setTemporaryMapper(null)
            }
        }

        val metadata = MapMetadata(name, description, author, width, height, meta.associate { it.key to it.value })
        return@runCatching metadata to floors
    }

    private fun getImage(name: String): BufferedImage =
        regions[
            name,
            {
                var image = images[name]
                if (image == null) {
                    logger
                    image = images["error"]!!
                }
                ImageIO.read(image.toFile())
            },
        ]

    private fun tint(image: BufferedImage, color: Color): BufferedImage {
        val copy = BufferedImage(image.width, image.height, image.type)
        val tmp = Color()
        for (x in 0 until copy.width) {
            for (y in 0 until copy.height) {
                val argb = image.getRGB(x, y)
                tmp.argb8888(argb)
                tmp.mul(color)
                copy.setRGB(x, y, tmp.argb8888())
            }
        }
        return copy
    }

    private fun conv(rgba: Int): Int {
        return Color(rgba).argb8888()
    }

    companion object {
        private val logger by LoggerDelegate()
        private val SCHEMATIC_PREVIEW_SCOPE = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
        private val MAP_PREVIEW_SCOPE = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
        private val SCHEMATIC_HEADER =
            byteArrayOf('m'.code.toByte(), 's'.code.toByte(), 'c'.code.toByte(), 'h'.code.toByte())
        private const val MAX_SCHEMATIC_SIDE_SIZE = 256
    }
}
