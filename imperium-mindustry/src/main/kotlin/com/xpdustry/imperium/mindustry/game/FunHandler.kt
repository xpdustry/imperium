package com.xpdustry.imperium.mindustry.game

import arc.util.Log
import arc.util.serialization.Jval
import com.xpdustry.distributor.api.command.CommandSender
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.command.annotation.ServerSide
import com.xpdustry.imperium.mindustry.translation.command_arg_unknown
import mindustry.Vars
import mindustry.gen.Groups
import mindustry.gen.Player

// This class's only purpose is for enjoyment

class FunHandler : ImperiumApplication.Listener {

    override fun onImperiumInit() {
        // TODO: rank check
        // Allows foos to teleport
        Vars.netServer.addPacketHandler("teleport") { _, data ->
            try {
                val json = Jval.read(data)
                val x = json.getFloat("x", 0f)
                val y = json.getFloat("y", 0f)
                val targetID = json.getInt("target", -1)
                val player = Groups.player.find { it.id == targetID }
                if (player != null) {
                    setUnitPosition(player, x, y)
                }
            } catch (e: Exception) {
                Log.err("Error handling teleport packet", e)
            }
        }
    }

    // TODO: Let active people troll as well? Could be used for cheating
    // in attack however, no use in pvp unless staff
    @ImperiumCommand(["teleport"], Rank.OVERSEER)
    @ClientSide
    @ServerSide
    fun onTeleportCommand(sender: CommandSender, x: Float, y: Float, player: Player? = null) {
        var players = player
        if(player == null) players = sender.player
        setUnitPosition(players, x, y)
    }

    fun setUnitPosition(player: Player, x: Float, y: Float) {
        player.unit().set(x, y)
    }

    @ImperiumCommand(["status"], Rank.MODERATOR)
    @ClientSide
    fun onStatusCommand(sender: CommandSender, status: String, length: String, player: Player? = null) {
        val statusEffect = Vars.content.statusEffect(status) ?: return sender.reply(command_arg_unknown(status))
        val players = player ?: sender.player
        val time = if (length == "infinite") Float.MAX_VALUE else length.toFloatOrNull() ?: return sender.reply(command_arg_unknown(length))
        players.unit().apply(statusEffect, time)
    }
}