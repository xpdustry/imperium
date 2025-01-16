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
package com.xpdustry.imperium.mindustry.formation

import arc.math.geom.Vec2
import com.xpdustry.distributor.api.annotation.EventHandler
import com.xpdustry.distributor.api.annotation.TriggerHandler
import com.xpdustry.distributor.api.command.CommandSender
import com.xpdustry.imperium.common.account.AccountManager
import com.xpdustry.imperium.common.account.Achievement
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.command.annotation.RequireAchievement
import com.xpdustry.imperium.mindustry.misc.asAudience
import com.xpdustry.imperium.mindustry.misc.runMindustryThread
import com.xpdustry.imperium.mindustry.misc.sessionKey
import com.xpdustry.imperium.mindustry.translation.formation_failure_dead
import com.xpdustry.imperium.mindustry.translation.formation_failure_no_valid_unit
import com.xpdustry.imperium.mindustry.translation.formation_failure_require_enabled
import com.xpdustry.imperium.mindustry.translation.formation_pattern_change
import com.xpdustry.imperium.mindustry.translation.formation_pattern_list
import com.xpdustry.imperium.mindustry.translation.formation_toggle
import kotlin.collections.set
import kotlin.math.min
import mindustry.Vars
import mindustry.content.UnitTypes
import mindustry.entities.Units
import mindustry.game.EventType
import mindustry.game.EventType.Trigger
import mindustry.gen.Groups
import mindustry.gen.Nulls
import mindustry.gen.Unit as MindustryUnit

class FormationListener(instances: InstanceManager) : ImperiumApplication.Listener {

    private val accounts = instances.get<AccountManager>()
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
            if (context.leader != player.unit()) {
                context.leader = player.unit()
            }
            for (member in context.members.toList()) {
                if (!member.isValid()) {
                    context.remove(member)
                }
            }
            if (context.slots > context.members.size) {
                val eligible = findEligibleFormationUnits(player.unit(), context.slots - context.members.size)
                for (unit in eligible) {
                    val ai = FormationAI(context)
                    unit.controller(ai)
                    context.members.add(ai)
                }
                if (eligible.isNotEmpty()) {
                    context.strategy.update(context)
                }
            }
            val anchor = Vec2(player.x(), player.y())
            for (member in context.members) {
                context.pattern.calculate(
                    member.targetVector,
                    context.assignments[member.id] ?: 0,
                    min(context.slots, context.members.size),
                    player.unit().hitSize * 1.8F,
                )
                member.targetVector.add(anchor)
            }
            if (context.members.isEmpty()) {
                context.deleted = true
                iterator.remove()
                player.asAudience.sendMessage(formation_failure_no_valid_unit())
            }
        }
    }

    @EventHandler
    fun onUnitControlEvent(event: EventType.UnitControlEvent) {
        val context = formations[event.player.id()] ?: return
        context.leader = event.unit
        context.members.toList().forEach { member ->
            if (!isEligibleFormationUnit(event.unit, member.backingUnit, formation = true)) {
                context.remove(member)
            }
        }
    }

    @ImperiumCommand(["group|g"])
    @ClientSide
    suspend fun onFormationCommand(sender: CommandSender) {
        val valid = runMindustryThread {
            if (sender.player.id() in formations) {
                formations.remove(sender.player.id())!!.deleted = true
                sender.reply(formation_toggle(enabled = false))
                return@runMindustryThread false
            }
            if (sender.player.unit().dead() || sender.player.unit() == Nulls.unit) {
                sender.error(formation_failure_dead())
                return@runMindustryThread false
            }
            true
        }

        if (!valid) {
            return
        }
        val account = accounts.selectBySession(sender.player.sessionKey)
        var slots = 4
        if (account != null) {
            slots =
                when {
                    accounts.selectAchievement(account.id, Achievement.HYPER) -> 18
                    accounts.selectAchievement(account.id, Achievement.ACTIVE) -> 12
                    account.rank >= Rank.VERIFIED -> 8
                    else -> slots
                }
        }

        runMindustryThread {
            val context = FormationContext(leader = sender.player.unit(), slots = slots)
            val eligible = findEligibleFormationUnits(sender.player.unit(), context.slots)
            if (eligible.isEmpty()) {
                sender.error(formation_failure_no_valid_unit())
                return@runMindustryThread
            }
            for (unit in eligible) {
                val ai = FormationAI(context)
                unit.controller(ai)
                context.members.add(ai)
            }
            context.strategy.update(context)
            formations[sender.player.id()] = context
            sender.reply(formation_toggle(enabled = true))
        }
    }

    @ImperiumCommand(["group|g", "pattern|p"])
    @RequireAchievement(Achievement.ACTIVE)
    @ClientSide
    fun onFormationPatternCommand(sender: CommandSender, pattern: FormationPatternEntry? = null) {
        if (pattern == null) {
            sender.reply(formation_pattern_list())
            return
        }
        val context = formations[sender.player.id()]
        if (context == null) {
            sender.reply(formation_failure_require_enabled())
            return
        }
        context.pattern = pattern.value
        context.strategy.update(context)
        sender.reply(formation_pattern_change(pattern))
    }

    private fun findEligibleFormationUnits(leader: MindustryUnit, slots: Int): List<MindustryUnit> {
        val result = arrayListOf<MindustryUnit>()
        Units.nearby(leader.team(), leader.x, leader.y, 30F * Vars.tilesize) { unit ->
            if (isEligibleFormationUnit(leader, unit)) {
                result += unit
            }
        }
        result.sortWith(compareBy({ leader.type == it.type }, { it.healthf() }))
        val available = min(result.size, slots)
        return result.subList(result.size - available, result.size)
    }

    private fun isEligibleFormationUnit(leader: MindustryUnit, unit: MindustryUnit, formation: Boolean = false) =
        unit.isAI &&
            unit.type != UnitTypes.mono &&
            unit.type.flying == leader.type.flying &&
            leader.type.buildSpeed > 0 == unit.type.buildSpeed > 0 &&
            unit != leader &&
            (unit.hitSize <= leader.hitSize * 1.5F) &&
            (unit.controller() !is FormationAI ||
                (formation && (unit.controller() as FormationAI).context.leader == leader))

    enum class FormationPatternEntry(val value: FormationPattern) {
        CIRCLE(CircleFormationPattern),
        SQUARE(SquareFormationPattern),
    }
}
