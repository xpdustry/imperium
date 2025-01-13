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
import com.xpdustry.imperium.common.account.AccountManager
import com.xpdustry.imperium.common.account.Achievement
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.command.annotation.RequireAchievement
import com.xpdustry.imperium.mindustry.misc.asAudience
import com.xpdustry.imperium.mindustry.misc.sessionKey
import com.xpdustry.imperium.mindustry.translation.formation_dead
import kotlin.collections.set // is this even used?
import kotlin.math.min
import kotlinx.coroutines.launch
import mindustry.Vars
import mindustry.content.UnitTypes
import mindustry.entities.Units
import mindustry.game.EventType.Trigger
import mindustry.gen.Groups
import mindustry.gen.Player
import mindustry.gen.Unit

class FormationListener(instances: InstanceManager) : ImperiumApplication.Listener {

    private val formations = mutableMapOf<Int, FormationContext>()
    private val manager: AccountManager = instances.get(AccountManager::class)

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
            // TODO: Replace units instead of disabling the formation
            if (context.members.isEmpty()) {
                context.deleted = true
                iterator.remove()
                player.asAudience.sendMessage(formation_dead())
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
            // Update formation members
            val newUnits = findEligibleFormationUnits(player, context, true)
            val newUnitTypes = newUnits.map { it.type }.toMutableList()
            val toChange = mutableListOf<Pair<FormationMember, FormationMember>>()
            for (member in context.members) {
                if (Groups.unit.getByID(member.id) != null &&
                    Groups.unit.getByID(member.id).type != player.unit().type &&
                    newUnitTypes.isNotEmpty() &&
                    player.unit().type == newUnitTypes.first()) {
                    if (newUnits.isNotEmpty()) {
                        newUnitTypes.removeFirst()
                        val a = FormationAI(player.unit(), context)
                        newUnits.first().controller(a)
                        newUnits.removeFirst()
                        toChange.add(Pair(member, a))
                    }
                }
            }
            if (toChange.isNotEmpty()) {
                for (change in toChange) {
                    val member = change.first
                    Groups.units.getByID(member.id).resetController()
                    context.members.remove(member)
                    context.members.add(change.second)
                }
                context.strategy.update(context)
            }
        }
    }

    @ImperiumCommand(["group|g"])
    @ClientSide
    fun onFormationCommand(sender: CommandSender) {
        ImperiumScope.MAIN.launch {
            var slots = 0
            val account = manager.selectBySession(sender.player.sessionKey)?.id
            if (account != null) {
                slots =
                    when {
                        // Only 1 person will have this
                        manager.selectAchievement(account, Achievement.ADDICT) -> 32
                        manager.selectAchievement(account, Achievement.HYPER) -> 16
                        manager.selectAchievement(account, Achievement.ACTIVE) -> 8
                        else -> 4
                    }
            } else {
                sender.reply("You must be logged in to use this command.")
                return@launch
            }

            if (sender.player.id() in formations) {
                formations.remove(sender.player.id())!!.deleted = true
                sender.reply("Formation disabled.")
            } else {
                if (sender.player.unit().dead()) {
                    sender.reply("You must be alive to enable formation.")
                    return@launch
                }
                val context =
                    FormationContext(
                        mutableListOf(),
                        mutableMapOf(),
                        slots,
                        CircleFormationPattern,
                        DistanceAssignmentStrategy)
                val eligible = findEligibleFormationUnits(sender.player, context, false)
                if (eligible.isEmpty()) {
                    sender.reply("No eligible units found.")
                    return@launch
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

    private fun findEligibleFormationUnits(
        player: Player,
        context: FormationContext,
        replace: Boolean
    ): MutableList<mindustry.gen.Unit> {
        val leader = player.unit()
        val result = mutableListOf<mindustry.gen.Unit>()
        Units.nearby(leader.team(), leader.x, leader.y, 30F * Vars.tilesize) {
            if (it.isAI &&
                it.type != UnitTypes.mono &&
                it.type.flying == leader.type.flying &&
                leader.type.buildSpeed > 0 == it.type.buildSpeed > 0 &&
                it != leader &&
                (it.hitSize <= leader.hitSize * 1.5f) &&
                it.controller() !is FormationAI) {
                result.add(it)
            }
        }
        // Prioritize units of the same type as the leader
        val (preferred, other) = result.partition { it.type == leader.type }
        return if (replace) {
            preferred.take(context.slots).toMutableList()
        } else {
            (preferred + other).take(context.slots).toMutableList()
        }
    }

    enum class FormationPatternEntry(val pattern: FormationPattern) {
        CIRCLE(CircleFormationPattern),
        SQUARE(SquareFormationPattern)
    }
}
