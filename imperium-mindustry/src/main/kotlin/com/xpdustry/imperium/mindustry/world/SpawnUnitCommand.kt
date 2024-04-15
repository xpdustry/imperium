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
package com.xpdustry.imperium.mindustry.world

import arc.math.Mathf
import com.xpdustry.distributor.annotation.method.EventHandler
import com.xpdustry.distributor.command.CommandSender
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.command.ImperiumPermission
import com.xpdustry.imperium.common.config.ServerConfig
import com.xpdustry.imperium.common.content.MindustryGamemode
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.game.MenuToPlayEvent
import com.xpdustry.imperium.mindustry.misc.Entities
import com.xpdustry.imperium.mindustry.misc.ImmutablePoint
import com.xpdustry.imperium.mindustry.misc.PlayerMap
import com.xpdustry.imperium.mindustry.misc.runMindustryThread
import mindustry.Vars
import mindustry.game.EventType
import mindustry.game.Team
import mindustry.UnitTypes
import mindustry.gen.Call
import mindustry.gen.Player

class SpawnUnitCommand(instances: InstanceManager) :
ImperiumApplication.Listener {
    
    @ImperiumCommand(["spawn-unit", "unit", "team", "amount", "x", "y"])
    @ImperiumPermission(Rank.ADMIN)
    @ClientSide
    private fun onSpawnUnitCommand(sender: CommandSender, type: unit , team: Team, amount: Int, x: Int, y: Int) {
        unit = UnitTypes.type
        if (x == null) { 
            x = sender.unit().x
        }
        if (y == null) { 
            y = sender.unit().y
        }
        if (unit == null) {
            return sender.sendMessage("${unit} is not a unit in mindustry")
        }
        
        for (i = 0, i > amount) { unit.spawn(team, x, y)}
        sender.sendMessage("Spawned ${amount} ${unit}s at ${x}, ${y}")
    }
}
   
// nonsense, ignore

/*
register("spawn <type> [team] [x] [y] [count]", Core.bundle.get("client.command.spawn.description")) { args, player ->
        val type = findUnit(args[0])
        val team = if (args.size < 2) player.team() else findTeam(args[1])
        val x = if (args.size < 3 || !Strings.canParsePositiveFloat(args[2])) player.x else args[2].toFloat() * tilesizeF
        val y = if (args.size < 4 || !Strings.canParsePositiveFloat(args[3])) player.y else args[3].toFloat() * tilesizeF
        val count = if (args.size < 5 || !Strings.canParsePositiveInt(args[4])) 1 else args[4].toInt()

        if (net.client()) Call.sendChatMessage("/js for(let i = 0; i < $count; i++) UnitTypes.$type.spawn(Team.all[${team.id}], $x, $y)")
        else repeat(count) {
            type.spawn(team, x, y)
        }
    }
/*
