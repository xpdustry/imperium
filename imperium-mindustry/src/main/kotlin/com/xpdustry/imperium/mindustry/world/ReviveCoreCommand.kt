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
    val coreList = mutableSetOf<CoreTile>()

    @EventHandler
    fun onBlockDestroy(event: EventType.BlockDestroyEvent) {
        if (event.tile.block() !is CoreBlock) return
        val block = event.tile.build
        // Will this crash if it somehow tries to add a not coreblock...
        // also this can be reduced im just too lazy...
        coreList.add(CoreTile(block.tileX(), block.tileY(), block.block as CoreBlock))
    }

    @ImperiumCommand(["revivecore|rc"])
    @ClientSide
    @ServerSide
    fun onReviveCommand(sender: CommandSender, core: Int) {
        val cores = coreList.toList()
        val revive =
            cores.getOrNull(core)
                ?: return sender.player.asAudience.sendMessage(translatable("imperium.revivecore.invalidcore", SCARLET))
        if (!canReviveCore(revive, sender)) return
        Vars.world.tile(revive.tileX, revive.tileY).setNet(revive.core as Block, sender.player.team(), 0)
        coreList.remove(revive)
    }

    @ImperiumCommand(["revivecore|rc", "list"])
    @ClientSide
    @ServerSide
    fun onReviveCommandList(sender: CommandSender, @Range(min = "1") page: Int) {
        val cores = coreList.toList()
        val entries = cores.chunked(5)
        val pages = StringBuilder()
        val pageEntry =
            entries.getOrNull(page - 1) ?: return sender.player.asAudience.sendMessage(invalid_revivecore_page())
        var counter: Int = 0 + (page - 1)
        for (entry in pageEntry) {
            pages.append(
                "[${counter}] ${entry.core.localizedName} (${entry.tileX}, ${entry.tileY}) - ${cost(entry.core)}\n"
            )
            counter++
        }
        sender.player.asAudience.sendMessage(corepage(pages.toString(), page, entries.size))
    }

    fun invalid_revivecore_page(): Component = components(SCARLET, translatable("imperium.revivecore.invalidpage"))

    fun corepage(page: String, pageNumber: Int, pageCount: Int): Component =
        components(
            WHITE,
            translatable("imperium.revivecore.pages", TranslationArguments.array(page, pageNumber, pageCount + 1)),
        )

    fun canReviveCore(core: CoreTile, sender: CommandSender): Boolean {
        return false
    }

    fun cost(core: CoreBlock): String {
        return ""
    }
}

data class CoreTile(val tileX: Int, val tileY: Int, val core: CoreBlock)
