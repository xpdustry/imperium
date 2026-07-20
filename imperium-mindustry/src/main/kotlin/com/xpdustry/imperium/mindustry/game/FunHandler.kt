// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.game

import arc.Core
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
import mindustry.type.UnitType
import mindustry.world.blocks.storage.CoreBlock
import org.incendo.cloud.annotation.specifier.Greedy

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

                        if (player != null && rank >= Rank.OVERSEER) {
                            setUnitPosition(player, x, y)
                        } else if (clients.isFooClient(sender) && navTp) {
                            if (blockIsCore(x.toInt(), y.toInt())) setUnitPosition(sender, x, y)
                        }
                    } catch (_: Exception) {}
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

    @ImperiumCommand(["statuseffect|status"], Rank.MODERATOR)
    @ClientSide
    fun onStatusCommand(sender: CommandSender, status: String, length: String, player: Player? = null) {
        val statusEffect = Vars.content.statusEffect(status) ?: return sender.reply(command_arg_unknown(status))
        val target = player ?: sender.player
        val time =
            if (length == "infinite") Float.POSITIVE_INFINITY
            else length.toFloatOrNull() ?: return sender.reply(command_arg_unknown(length))
        target.unit().apply(statusEffect, time)
        sender.reply("Added ${statusEffect.name} to ${target.plainName()}")
    }

    @ImperiumCommand(["changeunit|cu"], Rank.MODERATOR)
    @ClientSide
    fun onChangeUnitCommand(sender: CommandSender, unit: UnitType, target: Player = sender.player) {
        // Is all this necessary? Is there a better way
        val cunit = unit.create(target.team())
        val tunit = target.unit()
        cunit.x = tunit.x
        cunit.y = tunit.y
        cunit.rotation = tunit.rotation
        cunit.isShooting(tunit.isShooting)
        cunit.elevation(tunit.elevation)
        target.unit(cunit)
        // just in-case
        target.unit().add()
        sender.reply("Set ${target.plainName()}'s unit to ${unit.name}")
    }

    @ImperiumCommand(["changename"], Rank.MODERATOR)
    @ClientSide
    @ServerSide
    fun onNameChangeCommand(sender: CommandSender, target: Player, @Greedy name: String) {
        // How does this work with rainbow name enabled?
        target.name(name)
        sender.reply("Open tab list hehe")
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
