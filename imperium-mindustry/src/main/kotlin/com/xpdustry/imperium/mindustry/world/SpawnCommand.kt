// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.world

import arc.math.geom.Vec2
import com.xpdustry.distributor.api.Distributor
import com.xpdustry.distributor.api.command.CommandSender
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.dependency.Inject
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.command.annotation.Flag
import com.xpdustry.imperium.mindustry.translation.spawned
import kotlin.random.Random
import mindustry.Vars
import mindustry.game.Team
import mindustry.type.UnitType
import org.incendo.cloud.annotation.specifier.Range

@Inject
class SpawnCommand : ImperiumApplication.Listener {

    @ImperiumCommand(["spawn"], Rank.ADMIN)
    @ClientSide
    fun onUnitSpawnCommand(
        sender: CommandSender,
        unit: UnitType,
        @Range(min = "1", max = "300") count: Int = 1,
        team: Team = sender.player.team(),
        @Flag x: Int = sender.player.tileX(),
        @Flag y: Int = sender.player.tileY(),
        @Flag silent: Boolean,
    ) {
        var spawned = 0
        repeat(count) {
            val worldX = (x * Vars.tilesize) + Random.nextInt(Vars.tilesize * 2)
            val worldY = (y * Vars.tilesize) + Random.nextInt(Vars.tilesize * 2)
            val entity = unit.spawn(Vec2(worldX.toFloat(), worldY.toFloat()), team)
            if (!entity.dead()) spawned++
        }
        if (spawned == 0) {
            sender.error(
                // Not translated as all admins speak english
                "You reached the unit cap of ${unit.name} for the team ${team.coloredName()}."
            )
            return
        }
        if (silent) {
            sender.reply(
                // Not translated as all admins speak english
                "You spawned $spawned ${unit.name} for team ${team.coloredName()} at ($x, $y)."
            )
        } else {
            Distributor.get().audienceProvider.players.sendMessage(spawned(sender.player, spawned, unit, team, x, y))
        }
    }
}
