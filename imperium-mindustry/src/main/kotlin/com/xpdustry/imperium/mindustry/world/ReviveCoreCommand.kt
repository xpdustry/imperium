package com.xpdustry.imperium.mindustry.world

import com.xpdustry.distributor.api.annotation.EventHandler
import com.xpdustry.distributor.api.command.CommandSender
import com.xpdustry.distributor.api.component.Component
import com.xpdustry.distributor.api.component.ListComponent.components
import com.xpdustry.distributor.api.component.TranslatableComponent.translatable
import com.xpdustry.distributor.api.component.style.ComponentColor.*
import com.xpdustry.distributor.api.translation.TranslationArguments
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.command.annotation.ServerSide
import com.xpdustry.imperium.mindustry.misc.asAudience
import mindustry.Vars
import mindustry.game.EventType
import mindustry.world.Block
import mindustry.world.blocks.storage.CoreBlock
import org.incendo.cloud.annotation.specifier.Range

class ReviveCoreCommand : ImperiumApplication.Listener {
    // TODO: Make a utility class for tile references so we dont have
    // to keep using Int pairs for tile locations
    val cores = mutableListOf<CoreTile>()

    @EventHandler
    fun onBlockDestroy(event: EventType.BlockDestroyEvent) {
        if (event.tile.block() !is CoreBlock) return
        val block = event.tile.build
        // Will this crash if it somehow tries to add a not coreblock...
        // also this can be reduced im just too lazy...
        cores.add(CoreTile(block.tileX(), block.tileY(), block.block as CoreBlock))
    }

    @ImperiumCommand(["revivecore|rc"])
    @ClientSide
    @ServerSide
    fun onReviveCommand(sender: CommandSender, core: Int) {
        val revive = cores.get(core)
        Vars.world.tile(revive.tileX, revive.tileY).setNet(revive.core as Block, sender.player.team(), 0)
    }

    @ImperiumCommand(["revivecore|rc", "list"])
    @ClientSide
    @ServerSide
    fun onReviveCommandList(sender: CommandSender, @Range(min = "1") pagen: Int) {
        val pages = cores.chunked(5)
        val thingy = StringBuilder()
        val page = pages.getOrNull(pagen - 1) ?: return sender.player.asAudience.sendMessage(invalid_revivecore_page())
        var counter: Int = 0 + (pagen - 1)
        for (entry in page) {
            thingy.append("[${counter}] ${entry.core.localizedName} - (${entry.tileX}, ${entry.tileY})\n")
            counter++
        }
        corepage(thingy.toString(), pagen, pages.size)
    }

    fun invalid_revivecore_page(): Component = components(WHITE, translatable("imperium.revivecore.invalidpage"))

    fun corepage(page: String, pageNumber: Int, pageCount: Int): Component =
        components(
            WHITE,
            translatable("imperium.revivecore.pages", TranslationArguments.array(page, pageNumber, pageCount + 1))
        )
}

data class CoreTile(
    val tileX: Int,
    val tileY: Int,
    val core: CoreBlock,
)