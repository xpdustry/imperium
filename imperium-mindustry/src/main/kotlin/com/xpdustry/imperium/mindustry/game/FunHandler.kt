// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.game

import arc.Core
import arc.util.serialization.Jval
import com.xpdustry.distributor.api.annotation.TaskHandler
import com.xpdustry.distributor.api.command.CommandSender
import com.xpdustry.distributor.api.scheduler.MindustryTimeUnit
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
import mindustry.gen.Call
import mindustry.gen.Groups
import mindustry.gen.Player
import mindustry.type.UnitType
import mindustry.world.blocks.storage.CoreBlock
import org.incendo.cloud.annotation.specifier.Greedy

// This class's only purpose is for enjoyment

interface FunManager {
    fun changedName(player: Player): String?
}

@Inject
class FunHandler(
    @Named(IMPERIUM_SCOPE) private val scope: CoroutineScope,
    private val store: DataStoreService,
    private val clients: ClientDetector,
) : ImperiumApplication.Listener, FunManager {

    private val spawnedUnits: MutableSet<Int> = mutableSetOf()
    private val changedNames = mutableMapOf<Player, String>()

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
                            setUnitPosition(player, packet.x * 8, packet.y * 8)
                        } else if (clients.isFooClient(sender) && packet.navTp) {
                            // no deleting cores
                            if (sender.unit() is BlockUnitUnit) return@post
                            if (blockIsCore(packet.x.toInt(), packet.y.toInt(), sender.team())) {
                                setUnitPosition(sender, packet.x * 8, packet.y * 8)
                            }
                        }
                    } catch (e: Exception) {
                        logger.debug("Dropped invalid teleport packet from player {}", sender.id, e)
                    }
                }
            }
        }
    }

    @ImperiumCommand(["teleport|tp"], Rank.OVERSEER)
    @ClientSide
    @ServerSide
    fun onTeleportCommand(sender: CommandSender, x: Float, y: Float, player: Player? = null) {
        if (player == null && sender.isServer) return sender.reply("Console must provide a player")
        val target = player ?: sender.player
        setUnitPosition(target, x * 8, y * 8)
    }

    @ImperiumCommand(["statuseffect|status"], Rank.MODERATOR)
    @ClientSide
    fun onStatusCommand(sender: CommandSender, status: String, length: String, player: Player? = null) {
        val statusEffect = Vars.content.statusEffect(status) ?: return sender.reply(command_arg_unknown(status))
        val target = player ?: sender.player
        val time =
            if (length == "infinite") Float.POSITIVE_INFINITY
            else length.toFloatOrNull() ?: return sender.reply(command_arg_unknown(length))
        target.unit().apply(statusEffect, time * 60)
        sender.reply("Added ${statusEffect.name} to ${target.plainName()}")
    }

    @ImperiumCommand(["changeunit|cu"], Rank.MODERATOR)
    @ClientSide
    fun onChangeUnitCommand(sender: CommandSender, unit: UnitType, target: Player = sender.player) {
        val tunit = target.unit()

        val cunit = unit.create(target.team())
        cunit.x = tunit.x
        cunit.y = tunit.y
        cunit.rotation = tunit.rotation
        cunit.isShooting(tunit.isShooting)
        cunit.elevation(tunit.elevation)
        cunit.add()

        spawnedUnits.add(cunit.id)
        target.unit(cunit)
        // just in-case
        target.unit().add()
        sender.reply("Set ${target.plainName()}'s unit to ${unit.name}")

        if (tunit != null && (spawnedUnits.contains(tunit.id) || tunit.spawnedByCore)) {
            spawnedUnits.remove(tunit.id)
            Call.unitDespawn(tunit)
        }
    }

    @ImperiumCommand(["rename"], Rank.MODERATOR)
    @ClientSide
    @ServerSide
    fun onNameChangeCommand(sender: CommandSender, target: Player, @Greedy name: String) {
        changedNames[target] = name
        sender.reply("${target.name}'s name changed to $name")
    }

    override fun changedName(player: Player): String? {
        return changedNames[player]
    }

    // Cleans up changeunit spawned units
    @TaskHandler(delay = 1, interval = 1, unit = MindustryTimeUnit.SECONDS)
    fun onChangeUnitCheck() {
        val toRemove = mutableSetOf<Int>()
        for (unit in spawnedUnits) {
            val spawnedUnit = Groups.unit.find({ u -> u.id == unit }) ?: break
            if (!spawnedUnit.isPlayer) {
                Call.unitDespawn(spawnedUnit)
                toRemove.add(unit)
            }
        }
        if (!toRemove.isEmpty()) spawnedUnits.removeAll(toRemove)
    }

    fun setUnitPosition(player: Player, x: Float, y: Float) {
        // Will kill ground units if they cant walk on the tile
        player.unit().set(x, y)
        player.set(x, y)
        Call.setPosition(player.con, x, y)
        player.unit().snapInterpolation()
    }

    fun blockIsCore(x: Int, y: Int, team: Team): Boolean {
        val tile = Vars.world.tile(x, y)
        return tile != null && tile.block() is CoreBlock && tile.team() == team
    }

    private data class TeleportPacket(val x: Float, val y: Float, val targetId: Int, val navTp: Boolean)

    private companion object {
        private val logger by LoggerDelegate()
        private val TELEPORT_PACKET_FIELDS = setOf("x", "y", "targetId")

        private fun decodeTeleportPacket(data: String): TeleportPacket {
            val json = Jval.read(data)
            require(json.isObject) { "packet must be an object" }
            require(json.asObject().size <= TELEPORT_PACKET_FIELDS.size && TELEPORT_PACKET_FIELDS.all(json::has)) {
                "packet must contain exactly ${TELEPORT_PACKET_FIELDS.joinToString()}"
            }

            val xValue = json.getFloat("x", Float.NaN)
            require(!xValue.isNaN()) { "x must be a number" }
            require(xValue.toInt() in 0..<Vars.world.width()) { "x must be within world borders" }

            val yValue = json.getFloat("y", Float.NaN)
            require(!yValue.isNaN()) { "y must be a number" }
            require(yValue.toInt() in 0..<Vars.world.height()) { "y must be within world borders" }

            val navTpValue = json.getBool("navTp", false)

            val target = json.getInt("targetId", Integer.MIN_VALUE)
            require(target > -1) { "target must be a valid number" }

            return TeleportPacket(xValue, yValue, target, navTpValue)
        }
    }
}
