// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.world

import com.xpdustry.distributor.api.annotation.EventHandler
import com.xpdustry.distributor.api.command.CommandSender
import com.xpdustry.distributor.api.command.cloud.specifier.AllTeams
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.command.annotation.ServerSide
import com.xpdustry.imperium.mindustry.game.MenuToPlayEvent
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.toJavaDuration
import kotlin.time.toKotlinDuration
import mindustry.Vars
import mindustry.content.Blocks
import mindustry.content.Items
import mindustry.content.Planets
import mindustry.game.EventType
import mindustry.game.Gamemode
import mindustry.game.Team
import mindustry.gen.Call
import mindustry.type.Item
import mindustry.world.blocks.storage.CoreBlock
import org.incendo.cloud.annotation.specifier.Range

class ReviveCoreCommand : ImperiumApplication.Listener {
    private val destroyedCores = linkedMapOf<TilePosition, DestroyedCore>()
    private val reviveCooldowns = mutableMapOf<Team, Instant>()

    @EventHandler
    fun onBlockDestroyEvent(event: EventType.BlockDestroyEvent) {
        val building = event.tile.build as? CoreBlock.CoreBuild ?: return
        val position = TilePosition(building.tileX(), building.tileY())
        destroyedCores[position] = DestroyedCore(position.x, position.y, building.block as CoreBlock)
    }

    @EventHandler
    fun onMenuToPlayEvent(event: MenuToPlayEvent) {
        destroyedCores.clear()
        reviveCooldowns.clear()
    }

    @ImperiumCommand(["revivecore|rc"])
    @ClientSide
    @ServerSide
    fun onReviveCoreListShortcut(sender: CommandSender) {
        onReviveCoreListCommand(sender, 1)
    }

    @ImperiumCommand(["revivecore|rc"])
    @ClientSide
    fun onReviveCoreCommand(sender: CommandSender, @Range(min = "0") id: Int) {
        reviveCore(sender, sender.player.team(), id, client = true)
    }

    @ImperiumCommand(["revivecore|rc"])
    @ServerSide
    fun onReviveCoreCommand(sender: CommandSender, @AllTeams team: Team, @Range(min = "0") id: Int) {
        reviveCore(sender, team, id, client = false)
    }

    @ImperiumCommand(["revivecore|rc", "list"])
    @ClientSide
    @ServerSide
    fun onReviveCoreListCommand(sender: CommandSender, @Range(min = "1") page: Int = 1) {
        val cores = getDestroyedCores()
        if (cores.isEmpty()) {
            sender.reply("No destroyed cores can be revived right now.")
            return
        }

        val pages = cores.chunked(PAGE_SIZE)
        val pageEntries = pages.getOrNull(page - 1)
        if (pageEntries == null) {
            sender.error("Invalid revive core page. Use a page between 1 and ${pages.size}.")
            return
        }

        val firstId = (page - 1) * PAGE_SIZE
        sender.reply(
            buildString {
                appendLine("[accent]List of revivable cores[]")
                appendLine("------------------------------")
                pageEntries.forEachIndexed { index, core ->
                    appendLine(
                        "[orange]#${firstId + index}[] ${core.block.localizedName} " +
                            "[lightgray](${core.tileX}, ${core.tileY})[] - ${formatCost(reviveCost(core.block))}"
                    )
                }
                append("Page [accent]$page[] of [accent]${pages.size}[]")
            }
        )
    }

    private fun reviveCore(sender: CommandSender, team: Team, id: Int, client: Boolean) {
        val cores = getDestroyedCores()
        val core = cores.getOrNull(id)
        if (core == null) {
            sender.error("Invalid revive core id. Use /rc list to view revivable cores.")
            return
        }

        if (!isTileAvailable(core)) {
            destroyedCores.remove(TilePosition(core.tileX, core.tileY))
            sender.error("That core can no longer be revived.")
            return
        }

        val cooldown = getRemainingCooldown(team)
        if (cooldown > Duration.ZERO) {
            sender.error("Your team must wait ${formatDuration(cooldown)} before reviving another core.")
            return
        }

        val cost = reviveCost(core.block)
        val items = team.items()
        if (cost.any { !items.has(it.item, it.amount) }) {
            sender.error("Not enough items to revive ${core.block.localizedName}. Need ${formatCost(cost)}.")
            return
        }

        cost.forEach { items.remove(it.item, it.amount) }
        Call.constructFinish(
            Vars.world.tile(core.tileX, core.tileY),
            core.block,
            if (client) sender.player.unit() else null,
            0,
            team,
            false,
        )

        destroyedCores.remove(TilePosition(core.tileX, core.tileY))
        reviveCooldowns[team] = Instant.now().plus(COOLDOWN.toJavaDuration())
        sender.reply("Revived ${core.block.localizedName} at (${core.tileX}, ${core.tileY}) for ${formatCost(cost)}.")
    }

    private fun getDestroyedCores(): List<DestroyedCore> {
        destroyedCores.entries.removeIf { !isTileAvailable(it.value) }
        return destroyedCores.values.toList()
    }

    private fun isTileAvailable(core: DestroyedCore): Boolean =
        Vars.world.tile(core.tileX, core.tileY)?.block() == Blocks.air

    private fun reviveCost(core: CoreBlock): List<ItemRequirement> {
        val amount =
            core.size * if (Vars.state.rules.mode() == Gamemode.attack) ATTACK_COST_FACTOR else DEFAULT_COST_FACTOR
        val items =
            when (Vars.state.rules.planet) {
                Planets.erekir -> listOf(Items.carbide, Items.oxide)
                else -> listOf(Items.surgeAlloy, Items.blastCompound)
            }
        return items.map { item -> ItemRequirement(item, amount) }
    }

    private fun getRemainingCooldown(team: Team): Duration {
        val deadline = reviveCooldowns[team] ?: return Duration.ZERO
        val remaining = java.time.Duration.between(Instant.now(), deadline)
        return if (remaining.isNegative) Duration.ZERO else remaining.toKotlinDuration()
    }

    private fun formatCost(cost: List<ItemRequirement>): String =
        cost.joinToString(" + ") { requirement -> "[accent]${requirement.amount}[] ${requirement.item.localizedName}" }

    private fun formatDuration(duration: Duration): String =
        duration.toComponents { minutes, seconds, _ ->
            when {
                minutes > 0 && seconds > 0 -> "${minutes}m ${seconds}s"
                minutes > 0 -> "${minutes}m"
                else -> "${seconds}s"
            }
        }

    private data class TilePosition(val x: Int, val y: Int)

    private data class DestroyedCore(val tileX: Int, val tileY: Int, val block: CoreBlock)

    private data class ItemRequirement(val item: Item, val amount: Int)

    private companion object {
        const val PAGE_SIZE = 5
        const val DEFAULT_COST_FACTOR = 3000
        const val ATTACK_COST_FACTOR = 1000
        val COOLDOWN = 5.minutes
    }
}
