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
package com.xpdustry.imperium.mindustry.game.formation

import arc.math.geom.Vec2
import com.xpdustry.distributor.api.annotation.TriggerHandler
import com.xpdustry.distributor.api.command.CommandSender
import com.xpdustry.imperium.common.account.Achievement
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.command.annotation.RequireAchievement
import kotlin.collections.set
import kotlin.math.min
import mindustry.Vars
import mindustry.content.UnitTypes
import mindustry.entities.Units
import mindustry.game.EventType.Trigger
import mindustry.gen.Groups
import mindustry.gen.Player
import mindustry.gen.Unit

class FormationListener : ImperiumApplication.Listener {

    private val formations = mutableMapOf<Int, FormationContext>()

    @TriggerHandler(Trigger.update)
    fun onFormationUpdate() {
        val iterator = formations.iterator()
        while (iterator.hasNext()) {
            val (id, context) = iterator.next()
            val player = Groups.player.getByID(id)
            if (player == null) {
                context.deleted = true
                iterator.remove()
                continue
            }
            val anchor = Vec2(player.x(), player.y())
            for (member in context.members) {
                context.pattern.calculate(
                    member.targetVector,
                    context.assignments[member.id] ?: 0,
                    min(context.slots, context.members.size),
                    player.unit().hitSize * 1.6F)
                member.targetVector.add(anchor)
            }
        }
    }

    @ImperiumCommand(["group|g"])
    @RequireAchievement(Achievement.ACTIVE)
    @ClientSide
    fun onFormationCommand(sender: CommandSender) {
        if (sender.player.id() in formations) {
            formations.remove(sender.player.id())!!.deleted = true
            sender.reply("Formation disabled.")
        } else {
            if (sender.player.unit().dead()) {
                sender.reply("You must be alive to enable formation.")
                return
            }
            val context =
                FormationContext(
                    mutableListOf(),
                    mutableMapOf(),
                    8,
                    CircleFormationPattern,
                    DistanceAssignmentStrategy)
            val eligible = findEligibleFormationUnits(sender.player, context)
            if (eligible.isEmpty()) {
                sender.reply("No eligible units found.")
                return
            }
            for (unit in eligible) {
                val a = FormationAI(sender.player.unit(), context)
                unit.controller(a)
                context.members.add(a)
            }
            context.strategy.update(context)
            formations[sender.player.id()] = context
            sender.reply("Formation enabled.")
        }
    }

    @ImperiumCommand(["group|g", "pattern|p"])
    @RequireAchievement(Achievement.ACTIVE)
    @ClientSide
    fun onFormationPatternCommand(sender: CommandSender, pattern: FormationPatternEntry) {
        val context =
            formations[sender.player.id()]
                ?: run {
                    sender.reply("You must enable formation first.")
                    return
                }
        context.pattern = pattern.pattern
        context.strategy.update(context)
        sender.reply("Formation pattern set to ${pattern.name.lowercase()}.")
    }

    private fun findEligibleFormationUnits(player: Player, context: FormationContext): List<Unit> {
        val leader = player.unit()
        val result = mutableListOf<Unit>()
        Units.nearby(leader.team(), leader.x, leader.y, 30F * Vars.tilesize) {
            if (it.isAI &&
                it.type != UnitTypes.mono &&
                it.type.flying == leader.type.flying &&
                leader.type.buildSpeed > 0 == it.type.buildSpeed > 0 &&
                it != leader &&
                it.hitSize <= leader.hitSize * 1.1f &&
                it.controller() !is FormationAI) {
                result.add(it)
            }
        }
        return result.take(context.slots)
    }

    enum class FormationPatternEntry(val pattern: FormationPattern) {
        CIRCLE(CircleFormationPattern),
        SQUARE(SquareFormationPattern)
    }
}
