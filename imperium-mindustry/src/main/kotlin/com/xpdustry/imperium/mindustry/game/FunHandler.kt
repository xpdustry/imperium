// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.game

import arc.Core
import arc.util.Log
import arc.util.serialization.Jval
import com.xpdustry.distributor.api.command.CommandSender
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.IMPERIUM_SCOPE
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.dependency.Named
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.command.annotation.ServerSide
import com.xpdustry.imperium.mindustry.misc.sessionKey
import com.xpdustry.imperium.mindustry.store.DataStoreService
import com.xpdustry.imperium.mindustry.translation.command_arg_unknown
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import mindustry.Vars
import mindustry.gen.Groups
import mindustry.gen.Player
import mindustry.world.blocks.storage.CoreBlock

// This class's only purpose is for enjoyment

class FunHandler(
    @Named(IMPERIUM_SCOPE) private val scope: CoroutineScope,
    private val store: DataStoreService,
    private val clients: ClientDetector,
) : ImperiumApplication.Listener {

    override fun onImperiumInit() {
        Vars.netServer.addPacketHandler("teleport") { sender, data ->
            scope.launch {
                val rank = store.selectBySessionKey(sender.sessionKey)?.account?.rank ?: Rank.EVERYONE
                Core.app.post {
                    try {
                        val json = Jval.read(data)
                        val x = json.getFloat("x", 0f)
                        val y = json.getFloat("y", 0f)
                        val targetID = json.getInt("target", -1)
                        val player = Groups.player.find { it.id == targetID }
                        val navTp = json.getBool("navTp", false)

                        if (player != null && rank <= Rank.OVERSEER) {
                            setUnitPosition(player, x, y)
                        } else if (clients.isFooClient(sender) && navTp) {
                            if (blockIsCore(x.toInt(), y.toInt())) setUnitPosition(sender, x, y)
                        }
                    } catch (e: Exception) {
                        Log.err("Error handling teleport packet")
                    }
                }
            }
        }
    }

    @ImperiumCommand(["teleport"], Rank.OVERSEER)
    @ClientSide
    @ServerSide
    fun onTeleportCommand(sender: CommandSender, x: Float, y: Float, player: Player? = null) {
        if (player == null && sender.isServer) return sender.reply("Console must provide a player")
        val target = player ?: sender.player
        setUnitPosition(target, x, y)
    }

    @ImperiumCommand(["status"], Rank.MODERATOR)
    @ClientSide
    fun onStatusCommand(sender: CommandSender, status: String, length: String, player: Player? = null) {
        val statusEffect = Vars.content.statusEffect(status) ?: return sender.reply(command_arg_unknown(status))
        val target = player ?: sender.player
        val time =
            if (length == "infinite") Float.MAX_VALUE
            else length.toFloatOrNull() ?: return sender.reply(command_arg_unknown(length))
        target.unit().apply(statusEffect, time)
    }

    fun setUnitPosition(player: Player, x: Float, y: Float) {
        // Will kill ground units if they cant walk on the tile
        player.unit().set(x, y)
    }

    fun blockIsCore(x: Int, y: Int): Boolean {
        val tile = Vars.world.tile(x, y)
        return tile.block() is CoreBlock
    }
}
