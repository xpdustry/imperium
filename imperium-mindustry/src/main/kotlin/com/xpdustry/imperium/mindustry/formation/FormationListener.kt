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

import arc.math.Mathf
import arc.util.Interval
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
import com.xpdustry.imperium.mindustry.translation.formation_pattern_failure_no_permission
import com.xpdustry.imperium.mindustry.translation.formation_pattern_list
import com.xpdustry.imperium.mindustry.translation.formation_toggle
import java.util.ArrayDeque
import kotlin.collections.set
import kotlin.math.max
import kotlin.math.min
import mindustry.Vars
import mindustry.content.UnitTypes
import mindustry.entities.Units
import mindustry.game.EventType.Trigger
import mindustry.gen.Groups
import mindustry.gen.Nulls
import mindustry.gen.Unit as MindustryUnit

class FormationListener(instances: InstanceManager) : ImperiumApplication.Listener {

    private val interval = Interval()
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

            var updated = false
            for (member in context.members.toList()) {
                if (
                    !member.isValid() ||
                        !isEligibleFormationUnit(context.leader, member.backingUnit, acceptsFormationMembers = true)
                ) {
                    updated = true
                    context.remove(member)
                }
            }

            if (interval.get(60F) || context.members.isEmpty()) {
                val replaceable =
                    context.members
                        .asSequence()
                        .map { it to calculateScore(context.leader, it.backingUnit) }
                        .filter { (_, score) -> score < 1F }
                        .sortedByDescending { (_, score) -> score }
                        .toList()
                val eligible = ArrayDeque(findEligibleFormationUnits(player.unit()))
                for ((replacing, score1) in replaceable) {
                    val (unit, score2) = eligible.poll() ?: break
                    if (score1 < score2) {
                        updated = true
                        context.remove(replacing)
                        context.add(unit)
                    } else {
                        break
                    }
                }
                while (context.open > 0 && eligible.isNotEmpty()) {
                    updated = true
                    val (unit, _) = eligible.pop()
                    context.add(unit)
                }
            }

            if (updated) {
                context.strategy.update(context)
            }

            val spacing =
                if (context.leader.hitSize <= 15) context.leader.hitSize * 1.6F else context.leader.hitSize * 1.35F
            for (member in context.members) {
                context.pattern.calculate(
                    member.targetVector,
                    context.assignments[member.id] ?: 0,
                    min(context.slots, context.members.size),
                    spacing,
                    context.leader.speed(),
                )
                member.targetVector.add(player)
            }

            if (context.members.isEmpty()) {
                context.deleted = true
                iterator.remove()
                player.asAudience.sendMessage(formation_failure_no_valid_unit())
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
            val manual = accounts.selectMetadata(account.id, "formation_max_slots")?.toIntOrNull()
            if (manual != null) {
                slots = max(slots, manual)
            }
        }

        runMindustryThread {
            val context = FormationContext(leader = sender.player.unit(), slots = slots)
            val eligible = findEligibleFormationUnits(sender.player.unit()).take(context.slots)
            if (eligible.isEmpty()) {
                sender.error(formation_failure_no_valid_unit())
                return@runMindustryThread
            }
            for ((unit, _) in eligible) {
                context.add(unit)
            }
            context.strategy.update(context)
            formations[sender.player.id()] = context
            sender.reply(formation_toggle(enabled = true))
        }
    }

    @ImperiumCommand(["group|g", "pattern|p"])
    @RequireAchievement(Achievement.ACTIVE)
    @ClientSide
    suspend fun onFormationPatternCommand(sender: CommandSender, pattern: FormationPatternEntry? = null) {
        val rank = accounts.selectBySession(sender.player.sessionKey)?.rank ?: error("That ain't supposed to happen.")
        if (pattern == null) {
            sender.reply(formation_pattern_list(rank))
            return
        }
        val context = formations[sender.player.id()]
        if (context == null) {
            sender.reply(formation_failure_require_enabled())
            return
        }
        if (rank < pattern.rank) {
            return sender.reply(formation_pattern_failure_no_permission(pattern))
        }
        context.pattern = pattern.value
        context.strategy.update(context)
        sender.reply(formation_pattern_change(pattern))
    }

    private fun findEligibleFormationUnits(leader: MindustryUnit): MutableList<UnitWithScore> {
        val result = arrayListOf<Pair<MindustryUnit, Float>>()
        Units.nearby(leader.team(), leader.x, leader.y, 30F * Vars.tilesize) { unit ->
            if (isEligibleFormationUnit(leader, unit)) {
                result += unit to calculateScore(leader, unit)
            }
        }
        result.sortByDescending(Pair<MindustryUnit, Float>::second)
        return result
    }

    private fun isEligibleFormationUnit(
        leader: MindustryUnit,
        unit: MindustryUnit,
        acceptsFormationMembers: Boolean = false,
    ) =
        unit.isAI &&
            unit.team() == leader.team() &&
            unit.type != UnitTypes.mono &&
            unit.type.flying == leader.type.flying &&
            leader.type.buildSpeed > 0 == unit.type.buildSpeed > 0 &&
            unit != leader &&
            (unit.hitSize <= leader.hitSize * 1.5F) &&
            (unit.controller() !is FormationAI ||
                (acceptsFormationMembers && (unit.controller() as FormationAI).context.leader == leader))

    private fun calculateScore(leader: MindustryUnit, unit: MindustryUnit): Float {
        var score = 0F
        // TODO replace with JDK 21 Math#clamp when possible
        score += Mathf.clamp(unit.healthf() / 2F, 0F, 0.5F)
        score += if (leader.type() == unit.type()) 0.5F else 0F
        return score
    }

    enum class FormationPatternEntry(val value: FormationPattern, val rank: Rank = Rank.EVERYONE) {
        CIRCLE(CircleFormationPattern),
        SQUARE(SquareFormationPattern),
        ROTATING_CIRCLE(RotatingCircleFormationPattern, Rank.OVERSEER),
    }
}

private typealias UnitWithScore = Pair<MindustryUnit, Float>
