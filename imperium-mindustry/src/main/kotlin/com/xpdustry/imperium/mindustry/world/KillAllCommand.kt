// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.world

import com.xpdustry.distributor.api.command.CommandSender
import com.xpdustry.distributor.api.component.ListComponent.components
import com.xpdustry.distributor.api.component.NumberComponent.number
import com.xpdustry.distributor.api.component.TextComponent.space
import com.xpdustry.distributor.api.component.TranslatableComponent.translatable
import com.xpdustry.distributor.api.component.style.ComponentColor
import com.xpdustry.distributor.api.translation.TranslationArguments.array
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.content.MindustryGamemode
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.command.annotation.Flag
import com.xpdustry.imperium.mindustry.command.annotation.Scope
import com.xpdustry.imperium.mindustry.command.vote.AbstractVoteCommand
import com.xpdustry.imperium.mindustry.command.vote.Vote
import com.xpdustry.imperium.mindustry.command.vote.VoteManager
import com.xpdustry.imperium.mindustry.formation.FormationAI
import com.xpdustry.imperium.mindustry.misc.Entities
import com.xpdustry.imperium.mindustry.misc.runMindustryThread
import kotlin.time.Duration.Companion.seconds
import mindustry.game.Team
import mindustry.gen.Call
import mindustry.type.UnitType
import org.incendo.cloud.annotation.specifier.Range

class KillAllCommand(instances: InstanceManager) :
    AbstractVoteCommand<Unit>(instances.get(), "killall", instances.get(), 30.seconds), ImperiumApplication.Listener {

    @ImperiumCommand(["killall|ku"])
    @Scope(MindustryGamemode.SANDBOX)
    @ClientSide
    fun onKillAllUnitsCommand(sender: CommandSender) {
        onVoteSessionStart(sender.player, manager.session, Unit)
    }

    @ImperiumCommand(["killall|ku", "y"])
    @Scope(MindustryGamemode.SANDBOX)
    @ClientSide
    fun onKillAllUnitsYesCommand(sender: CommandSender) {
        onPlayerVote(sender.player, manager.session, Vote.YES)
    }

    @ImperiumCommand(["killall|ku", "n"])
    @Scope(MindustryGamemode.SANDBOX)
    @ClientSide
    fun onKillAllUnitsNoCommand(sender: CommandSender) {
        onPlayerVote(sender.player, manager.session, Vote.NO)
    }

    @ImperiumCommand(["killall|ku", "cancel|c"], Rank.OVERSEER)
    @Scope(MindustryGamemode.SANDBOX)
    @ClientSide
    fun onKillAllUnitsCancelCommand(sender: CommandSender) {
        onPlayerCancel(sender.player, manager.session)
    }

    @ImperiumCommand(["killall|ku", "force|f"], Rank.OVERSEER)
    @Scope(MindustryGamemode.SANDBOX)
    @ClientSide
    fun onKillAllUnitsForceCommand(sender: CommandSender) {
        onPlayerForceSuccess(sender.player, manager.session)
    }

    @ImperiumCommand(["killall|ku"], Rank.MODERATOR)
    @Scope(MindustryGamemode.SANDBOX)
    @ClientSide
    fun onKillAllUnitsTeamCommand(
        sender: CommandSender,
        @Flag("u") type: UnitType? = null,
        @Flag("t") team: Team? = null,
        @Flag("c") @Range(min = "1") count: Int? = null,
    ) {
        var killed = 0
        for (unit in Entities.getUnits().toList()) {
            if (
                !unit.isPlayer &&
                    (team == null || team == unit.team()) &&
                    unit.controller() !is FormationAI &&
                    (type == null || unit.type() == type)
            ) {
                killed++
                Call.unitDespawn(unit)
                if (count != null && killed >= count) {
                    break
                }
            }
        }

        val components = components()
        components.append(
            translatable("imperium.killall.force.success.base", array(number(killed, ComponentColor.ACCENT)))
        )
        if (type != null) {
            components.append(
                space(),
                translatable("imperium.killall.force.success.type", array(translatable(type, ComponentColor.ACCENT))),
            )
        }
        if (team != null) {
            components.append(
                space(),
                translatable("imperium.killall.force.success.team", array(translatable(team, ComponentColor.ACCENT))),
            )
        }
        sender.reply(components.build())
    }

    override fun getVoteSessionDetails(session: VoteManager.Session<Unit>): String =
        "Type [orange]/killall <y/n>[] to kill all units."

    override suspend fun onVoteSessionSuccess(session: VoteManager.Session<Unit>) {
        runMindustryThread {
            Entities.getUnits().toList().forEach { unit ->
                if (!unit.isPlayer && unit.controller() !is FormationAI) Call.unitDespawn(unit)
            }
        }
    }
}
