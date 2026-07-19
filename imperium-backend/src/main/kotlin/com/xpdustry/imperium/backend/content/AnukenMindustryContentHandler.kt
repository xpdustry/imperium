// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.backend.content

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
import arc.mock.MockSettings
import arc.struct.StringMap
import arc.util.Log
import arc.util.io.CounterInputStream
import com.google.common.cache.CacheBuilder
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.dependency.Inject
import com.xpdustry.imperium.common.dependency.Named
import com.xpdustry.imperium.common.misc.logger
import com.xpdustry.imperium.common.misc.stripMindustryColors
import java.awt.Graphics2D
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.io.DataInputStream
import java.io.File
import java.io.InputStream
import java.nio.file.FileSystem
import java.nio.file.FileSystemAlreadyExistsException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration
import java.util.zip.InflaterInputStream
import javax.imageio.ImageIO
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import mindustry.Vars
import mindustry.content.Blocks
import mindustry.core.ContentLoader
import mindustry.core.GameState
import mindustry.core.Version
import mindustry.core.World
import mindustry.game.Team
import mindustry.io.MapIO
import mindustry.io.SaveIO
import mindustry.world.Block
import mindustry.world.CachedTile
import mindustry.world.Tile
import mindustry.world.WorldContext
import mindustry.world.blocks.environment.OreBlock
import org.slf4j.event.Level

@Inject
class AnukenMindustryContentHandler(@Named("directory") directory: Path) :
    MindustryContentHandler, ImperiumApplication.Listener {

    private val mapPreviewDispatcher = Dispatchers.IO.limitedParallelism(1)

    private var currentSchematicGraphics: Graphics2D? = null
    private var currentSchematicImage: BufferedImage? = null

    private val directory = directory.resolve("mindustry-assets")
    private val bundledSpritesVersion by lazy {
        javaClass.getResourceAsStream("/sprites/sprites.aatls").use { input ->
            requireNotNull(input) { "Missing bundled Mindustry sprites" }
            input.readBytes().contentHashCode().toString()
        }
    }
    private val pageImages = mutableMapOf<Texture, BufferedImage>()
    private val atlasRegions = mutableMapOf<String, TextureAtlasData.Region>()
    private val regions =
        CacheBuilder.newBuilder().expireAfterAccess(Duration.ofMinutes(1)).build<String, BufferedImage>()

    override fun onImperiumInit() {
        Version.enabled = false
        Vars.headless = true
        Core.settings = MockSettings()

        Log.useColors = false
        Log.logger = logger@{ level, text ->
            val toSlf4jLevel =
                when (level) {
                    Log.LogLevel.debug -> Level.DEBUG
                    Log.LogLevel.info -> Level.INFO
                    Log.LogLevel.warn -> Level.WARN
                    Log.LogLevel.err -> Level.ERROR
                    Log.LogLevel.none -> return@logger
                }
            logger.atLevel(toSlf4jLevel).log(Log.removeColors(text))
        }

        Vars.content = ContentLoader()
        Vars.content.createBaseContent()
        Vars.content.init()

        prepareBundledSprites()

        Vars.state = GameState()
        Core.atlas = TextureAtlas()
        pageImages.clear()
        atlasRegions.clear()

        val data =
            TextureAtlasData(
                Fi(directory.resolve("sprites/sprites.aatls").toFile()),
                Fi(directory.resolve("sprites").toFile()),
                false,
            )

        data.pages.forEach {
            it.texture = Texture.createEmpty(null)
            it.texture.width = it.width
            it.texture.height = it.height
            pageImages[it.texture] = ImageIO.read(it.textureFile.file())
        }

        data.regions.forEach {
            atlasRegions[it.name] = it
            Core.atlas.addRegion(
                it.name,
                AtlasRegion(
                        it.page.texture,
                        it.left,
                        it.top,
                        if (it.rotate) it.height else it.width,
                        if (it.rotate) it.width else it.height,
                    )
                    .apply {
                        name = it.name
                        offsetX = it.offsetX
                        offsetY = it.offsetY
                        originalWidth = it.originalWidth
                        originalHeight = it.originalHeight
                        rotate = it.rotate
                        splits = it.splits
                        pads = it.pads
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
                    var scaledX = x + 4f
                    var scaledY = y + 4f
                    var scaledWidth = w * 4f
                    var scaledHeight = h * 4f
                    scaledX *= 4f
                    scaledY *= 4f
                    scaledY = currentSchematicImage!!.height - (scaledY + scaledHeight / 2f) - scaledHeight / 2f

                    val affine = AffineTransform()
                    affine.translate(scaledX.toDouble(), scaledY.toDouble())
                    affine.rotate(
                        (-rotation * Mathf.degRad).toDouble(),
                        (originX * 4).toDouble(),
                        (originY * 4).toDouble(),
                    )
                    currentSchematicGraphics!!.transform = affine

                    val image = getImage(region as AtlasRegion)
                    currentSchematicGraphics!!.drawImage(image, 0, 0, scaledWidth.toInt(), scaledHeight.toInt(), null)
                }

                override fun draw(texture: Texture, spriteVertices: FloatArray, offset: Int, count: Int) = Unit
            }

        Vars.content.load()

        val colors = ImageIO.read(directory.resolve("sprites/block_colors.png").toFile())
        for (block in Vars.content.blocks()) {
            block.mapColor.argb8888(colors.getRGB(block.id.toInt(), 0))
            (block as? OreBlock)?.mapColor?.set(block.itemDrop.color)
        }

        Vars.world =
            object : World() {
                override fun tile(x: Int, y: Int): Tile = Tile(x, y)
            }
    }

    override suspend fun parseMap(file: File): Result<ParsedMindustryMapMetadata> =
        withContext(Dispatchers.IO) { file.inputStream().use(::parseMapMetadata0) }

    override suspend fun renderMap(file: File): Result<BufferedImage> =
        withContext(mapPreviewDispatcher) { file.inputStream().use { input -> renderMap0(input) } }

    private fun parseMapMetadata0(input: InputStream): Result<ParsedMindustryMapMetadata> =
        readMap0(input, preview = false).map { (metadata, _) -> metadata }

    private fun renderMap0(input: InputStream): Result<BufferedImage> =
        readMap0(input, preview = true).map { (_, preview) -> preview ?: error("Missing map preview") }

    private fun readMap0(
        input: InputStream,
        preview: Boolean,
    ): Result<Pair<ParsedMindustryMapMetadata, BufferedImage?>> = runCatching {
        val counter = CounterInputStream(InflaterInputStream(input))
        val stream = DataInputStream(counter)
        SaveIO.readHeader(stream)

        val version = stream.readInt()
        val reader = SaveIO.getSaveWriter(version)
        val metadataHolder = arrayOf<StringMap?>(null)

        reader.readRegion("meta", stream, counter) { metadataHolder[0] = reader.readStringMap(it) }

        val metadata = metadataHolder[0] ?: error("Missing map metadata")
        val parsed =
            ParsedMindustryMapMetadata(
                name = metadata["name"]?.stripMindustryColors().orEmpty().ifBlank { "Unknown" },
                description = metadata["description"]?.stripMindustryColors().orEmpty(),
                author = metadata["author"]?.stripMindustryColors().orEmpty().ifBlank { "Unknown" },
                width = metadata.getInt("width"),
                height = metadata.getInt("height"),
            )

        if (!preview) {
            return@runCatching parsed to null
        }

        val floors = BufferedImage(parsed.width, parsed.height, BufferedImage.TYPE_INT_ARGB)
        val walls = BufferedImage(parsed.width, parsed.height, BufferedImage.TYPE_INT_ARGB)
        val graphics = floors.createGraphics()
        val shadow = java.awt.Color(0, 0, 0, 64)
        val black = 255
        val tile =
            object : CachedTile() {
                override fun setBlock(type: Block) {
                    super.setBlock(type)
                    val color = MapIO.colorFor(block(), Blocks.air, Blocks.air, team())
                    if (color != black && color != 0) {
                        walls.setRGB(x.toInt(), floors.height - 1 - y, convertColor(color))
                        graphics.color = shadow
                        graphics.drawRect(x.toInt(), floors.height - 1 - y + 1, 1, 1)
                    }
                }
            }

        try {
            reader.readRegion("content", stream, counter) { reader.readContentHeader(it) }
            if (version >= 11) reader.readRegion("content", stream, counter, reader::skipContentPatches)
            reader.readRegion("preview_map", stream, counter) {
                reader.readMap(
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
                            if (tile.build == null) {
                                return
                            }

                            val color = tile.build.team.color.argb8888()
                            val size = tile.block().size
                            val offsetX = -(size - 1) / 2
                            val offsetY = -(size - 1) / 2
                            for (dx in 0 until size) {
                                for (dy in 0 until size) {
                                    val drawX = tile.x + dx + offsetX
                                    val drawY = tile.y + dy + offsetY
                                    walls.setRGB(drawX, floors.height - 1 - drawY, color)
                                }
                            }
                        }

                        override fun tile(index: Int): Tile {
                            tile.x = (index % parsed.width).toShort()
                            tile.y = (index / parsed.width).toShort()
                            return tile
                        }

                        override fun create(x: Int, y: Int, floorID: Int, overlayID: Int, wallID: Int): Tile {
                            floors.setRGB(
                                x,
                                floors.height - 1 - y,
                                convertColor(
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
        } finally {
            graphics.drawImage(walls, 0, 0, null)
            graphics.dispose()
            Vars.content.setTemporaryMapper(null)
        }

        parsed to floors
    }

    private fun prepareBundledSprites() {
        val versionFile = directory.resolve("VERSION.txt")
        if (Files.exists(versionFile) && versionFile.readText() == bundledSpritesVersion) {
            return
        }

        if (Files.exists(directory)) {
            Files.walk(directory).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
            }
        }

        Files.createDirectories(directory)
        copyBundledSpritesDirectory(directory.resolve("sprites"))
        versionFile.writeText(bundledSpritesVersion)
    }

    private fun copyBundledSpritesDirectory(target: Path) {
        val resource =
            javaClass.getResource("/sprites/sprites.aatls")?.toURI() ?: error("Missing bundled Mindustry sprites")
        if (resource.scheme == "jar") {
            val (fileSystem, close) = openJarFileSystem(resource)
            try {
                copyDirectory(fileSystem.getPath("/sprites"), target)
            } finally {
                if (close) {
                    fileSystem.close()
                }
            }
        } else {
            copyDirectory(Path.of(resource).parent, target)
        }
    }

    private fun openJarFileSystem(resource: java.net.URI): Pair<FileSystem, Boolean> =
        try {
            FileSystems.newFileSystem(resource, emptyMap<String, Any>()) to true
        } catch (_: FileSystemAlreadyExistsException) {
            FileSystems.getFileSystem(resource) to false
        }

    private fun copyDirectory(source: Path, target: Path) {
        Files.walk(source).use { paths ->
            paths.forEach { entry ->
                val destination = target.resolve(source.relativize(entry).toString())
                if (Files.isDirectory(entry)) {
                    Files.createDirectories(destination)
                } else {
                    Files.createDirectories(destination.parent)
                    Files.copy(entry, destination, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }
    }

    private fun getImage(region: AtlasRegion): BufferedImage =
        regions[
            region.name,
            {
                val source = atlasRegions[region.name] ?: atlasRegions["error"] ?: error("Missing atlas error region")
                extractRegionImage(source)
            },
        ]

    private fun extractRegionImage(region: TextureAtlasData.Region): BufferedImage {
        val page = pageImages[region.page.texture] ?: error("Missing atlas page image for ${region.name}")
        return if (region.rotate) {
            val source = page.getSubimage(region.left, region.top, region.height, region.width)
            rotateClockwise(source, region.width, region.height)
        } else {
            copyImage(page.getSubimage(region.left, region.top, region.width, region.height))
        }
    }

    private fun rotateClockwise(source: BufferedImage, width: Int, height: Int): BufferedImage {
        val rotated = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        for (x in 0 until width) {
            for (y in 0 until height) {
                rotated.setRGB(x, y, source.getRGB(y, width - 1 - x))
            }
        }
        return rotated
    }

    private fun copyImage(source: BufferedImage): BufferedImage {
        val copy = BufferedImage(source.width, source.height, BufferedImage.TYPE_INT_ARGB)
        val graphics = copy.createGraphics()
        graphics.drawImage(source, 0, 0, null)
        graphics.dispose()
        return copy
    }

    private fun convertColor(rgba: Int): Int = Color(rgba).argb8888()

    companion object {
        private val logger = logger<AnukenMindustryContentHandler>()
    }
}
