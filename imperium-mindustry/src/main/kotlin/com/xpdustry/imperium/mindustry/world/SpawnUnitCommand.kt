/*
 * Imperium, the software collection powering the Xpdustry network.
 * Copyright (C) 2023  Xpdustry
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
import arc.math.geom.Vec2
import com.xpdustry.distributor.command.CommandSender
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.command.ImperiumPermission
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import kotlin.random.Random
import mindustry.Vars
import mindustry.game.Team
import mindustry.gen.Call
import mindustry.type.UnitType
import org.incendo.cloud.annotation.specifier.Range

class SpawnCommand : ImperiumApplication.Listener {

    @ImperiumCommand(["spawn"])
    @ImperiumPermission(Rank.ADMIN)
    @ClientSide
    fun onUnitSpawnCommand(
        sender: CommandSender,
        unit: UnitType,
        @Range(min = "1", max = "300") count: Int = 1,
        team: Team = sender.player.team(),
        x: Int = sender.player.tileX(),
        y: Int = sender.player.tileY(),
        silent: Boolean = false,
    ) {
        repeat(count) {
            val worldX = (x * Vars.tilesize) + Random.nextInt(Vars.tilesize * 2)
            val worldY = (y * Vars.tilesize) + Random.nextInt(Vars.tilesize * 2)
            val position = Vec2(worldX.toFloat(), worldY.toFloat())
            unit.spawn(position, team)
        }
        if (silent == false) {
            Call.sendMessage(
                "${sender.name()} spawned $count ${unit.name} for team ${team.coloredName()} at ($x, $y).")
        } else {
            sender.sendMessage(
                "[lightgray]Silent:[] You spawned $count ${unit.name} for team ${team.coloredName()} at ($x, $y).")
        }
    }
}
