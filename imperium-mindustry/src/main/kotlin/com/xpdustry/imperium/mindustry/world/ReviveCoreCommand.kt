package com.xpdustry.imperium.mindustry.world

import com.xpdustry.distributor.api.annotation.EventHandler
import com.xpdustry.distributor.api.command.CommandSender
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.command.annotation.ServerSide
import mindustry.game.EventType
import mindustry.world.Block
import mindustry.world.blocks.storage.CoreBlock

class ReviveCoreCommand : ImperiumApplication.Listener {
    // TODO: Make a utility class for tile references so we dont have
    // to keep using Int pairs for tile locations
    val cores = mutableMapOf<Pair<Int, Int>, Block>()

    @EventHandler
    fun onBlockDestroy(event: EventType.BlockDestroyEvent) {
        if (event.tile.block() !is CoreBlock) return
        val block = event.tile.build
        cores[Pair(block.tileX(), block.tileY())] = block.block
    }

    @ImperiumCommand(["revivecore|rc"], Rank.EVERYONE)
    @ClientSide
    @ServerSide
    fun onReviveCommand(sender: CommandSender) {
        sender.reply("nuh uh")
    }
}