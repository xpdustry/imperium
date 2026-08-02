// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.game

import arc.Core
import arc.util.serialization.Jval
import com.xpdustry.distributor.api.command.CommandSender
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.IMPERIUM_SCOPE
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.dependency.Inject
import com.xpdustry.imperium.common.dependency.Named
import com.xpdustry.imperium.common.misc.LoggerDelegate
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.command.annotation.ServerSide
import com.xpdustry.imperium.mindustry.misc.sessionKey
import com.xpdustry.imperium.mindustry.store.DataStoreService
import com.xpdustry.imperium.mindustry.translation.command_arg_unknown
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import mindustry.Vars
import mindustry.game.Team
import mindustry.gen.BlockUnitUnit
import mindustry.gen.Groups
import mindustry.gen.Player
import mindustry.type.UnitType
import mindustry.world.blocks.storage.CoreBlock
import org.incendo.cloud.annotation.specifier.Greedy

// This class's only purpose is for enjoyment

@Inject
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
                    val packet =
                        try {
                            decodeTeleportPacket(data)
                        } catch (e: Exception) {
                            logger.debug("Dropped invalid teleport packet from player {}", sender.id, e)
                            return@post
                        }
                    val player = Groups.player.find { it.id == packet.targetId }

                    try {
                        if (player != null && rank >= Rank.OVERSEER) {
                            setUnitPosition(player, packet.x, packet.y)
                        } else if (clients.isFooClient(sender) && packet.navTp) {
                            // no deleting cores
                            if (sender.unit() is BlockUnitUnit) return@post
                            if (blockIsCore(packet.x.toInt(), packet.y.toInt(), sender.team())) {
                                setUnitPosition(sender, packet.x, packet.y)
                            }
                        }
                    } catch (e: Exception) {
                        logger.debug("Dropped invalid teleport packet from player {}", sender.id, e)
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

    fun blockIsCore(x: Int, y: Int, team: Team): Boolean {
        val tile = Vars.world.tile(x, y)
        return tile != null && tile.block() is CoreBlock && tile.team() == team
    }

    private data class TeleportPacket(val x: Float, val y: Float, val targetId: Int, val navTp: Boolean)

    private companion object {
        private val logger by LoggerDelegate()
        private val TELEPORT_PACKET_FIELDS = setOf("x", "y", "target", "navTp")

        private fun decodeTeleportPacket(data: String): TeleportPacket {
            val json = Jval.read(data)
            require(json.isObject) { "packet must be an object" }
            require(json.asObject().size == TELEPORT_PACKET_FIELDS.size && TELEPORT_PACKET_FIELDS.all(json::has)) {
                "packet must contain exactly ${TELEPORT_PACKET_FIELDS.joinToString()}"
            }

            val xValue = json.get("x")
            require(xValue.isNumber) { "x must be a number" }
            val x = xValue.asFloat()
            require(x.isFinite()) { "x must be finite" }

            val yValue = json.get("y")
            require(yValue.isNumber) { "y must be a number" }
            val y = yValue.asFloat()
            require(y.isFinite()) { "y must be finite" }

            val targetValue = json.get("target")
            require(targetValue.isNumber && targetValue.asNumber() is Long) { "target must be an integer" }
            val target = targetValue.asLong()
            require(target in Int.MIN_VALUE..Int.MAX_VALUE) { "target is outside the integer range" }

            val navTpValue = json.get("navTp")
            require(navTpValue.isBoolean) { "navTp must be a boolean" }

            return TeleportPacket(x, y, target.toInt(), navTpValue.asBool())
        }
    }
}
