// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.world

import com.xpdustry.distributor.api.annotation.EventHandler
import com.xpdustry.distributor.api.command.CommandSender
import com.xpdustry.distributor.api.command.cloud.specifier.AllTeams
import com.xpdustry.distributor.api.component.TextComponent.text
import com.xpdustry.distributor.api.gui.Action
import com.xpdustry.distributor.api.gui.BiAction
import com.xpdustry.distributor.api.gui.Window
import com.xpdustry.distributor.api.gui.menu.ListTransformer
import com.xpdustry.distributor.api.gui.menu.MenuManager
import com.xpdustry.distributor.api.plugin.MindustryPlugin
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.command.annotation.ServerSide
import com.xpdustry.imperium.mindustry.game.MenuToPlayEvent
import com.xpdustry.imperium.mindustry.misc.component1
import com.xpdustry.imperium.mindustry.misc.component2
import com.xpdustry.imperium.mindustry.misc.component3
import com.xpdustry.imperium.mindustry.misc.key
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
import mindustry.gen.Player
import mindustry.type.Item
import mindustry.world.blocks.storage.CoreBlock
import org.incendo.cloud.annotation.specifier.Range

class ReviveCoreCommand(instances: InstanceManager) : ImperiumApplication.Listener {
    private val destroyedCores = linkedMapOf<TilePosition, DestroyedCore>()
    private val reviveCooldowns = mutableMapOf<Team, Instant>()
    private val menu = createMenu(instances.get())

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
    fun onReviveCoreCommand(sender: CommandSender) {
        val cores = getDestroyedCores()
        if (cores.isEmpty()) {
            sender.reply("No destroyed cores can be revived right now.")
            return
        }
        menu.create(sender.player).show()
    }

    @ImperiumCommand(["revivecore|rc", "list"])
    @ClientSide
    fun onReviveCoreListCommand(sender: CommandSender) {
        onReviveCoreCommand(sender)
    }

    @ImperiumCommand(["revivecore|rc"])
    @ServerSide
    fun onReviveCoreCommand(sender: CommandSender, @AllTeams team: Team, @Range(min = "0") id: Int) {
        val core = getDestroyedCores().getOrNull(id)
        if (core == null) {
            sender.error("Invalid revive core id. Use `rc list` to view revivable cores.")
            return
        }

        val result = reviveCore(team, core, null)
        if (result.success) {
            sender.reply(result.message)
        } else {
            sender.error(result.message)
        }
    }

    @ImperiumCommand(["revivecore|rc", "list"])
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
                appendLine("List of revivable cores")
                appendLine("------------------------------")
                pageEntries.forEachIndexed { index, core -> appendLine("#${firstId + index} ${renderCore(core)}") }
                append("Page $page of ${pages.size}")
            }
        )
    }

    private fun createMenu(plugin: MindustryPlugin): MenuManager =
        MenuManager.create(plugin).apply {
            addTransformer { (pane, _, viewer) ->
                pane.title = text("Revive Core")
                pane.content = text(menuContent(viewer))
            }
            addTransformer(
                ListTransformer<DestroyedCore>()
                    .setProvider { getDestroyedCores() }
                    .setRenderer { core -> text(renderCore(core)) }
                    .setHeight(8)
                    .setChoiceAction(
                        BiAction.with(REVIVE_CORE)
                            .then(BiAction.from(Action.delegate(this@ReviveCoreCommand::onReviveCoreSelection)))
                    )
            )
        }

    private fun onReviveCoreSelection(window: Window): Action {
        val core = window.state[REVIVE_CORE]!!
        val result = reviveCore(window.viewer.team(), core, window.viewer)
        val action =
            if (result.success) {
                Action.hideAll()
            } else {
                Action(Window::show)
            }
        return action.then(Action.audience { it.sendAnnouncement(text(result.message)) })
    }

    private fun reviveCore(team: Team, core: DestroyedCore, player: Player?): ReviveResult {
        if (!isTileAvailable(core)) {
            destroyedCores.remove(TilePosition(core.tileX, core.tileY))
            return ReviveResult(false, "That core can no longer be revived.")
        }

        val cooldown = getRemainingCooldown(team)
        if (cooldown > Duration.ZERO) {
            return ReviveResult(false, "Your team must wait ${formatDuration(cooldown)} before reviving another core.")
        }

        val cost = reviveCost(core.block)
        val items = team.items()
        if (cost.any { !items.has(it.item, it.amount) }) {
            return ReviveResult(
                false,
                "Not enough items to revive ${core.block.localizedName}. Need ${formatCost(cost)}.",
            )
        }

        cost.forEach { items.remove(it.item, it.amount) }
        Call.constructFinish(Vars.world.tile(core.tileX, core.tileY), core.block, player?.unit(), 0, team, false)

        destroyedCores.remove(TilePosition(core.tileX, core.tileY))
        reviveCooldowns[team] = Instant.now().plus(COOLDOWN.toJavaDuration())
        return ReviveResult(
            true,
            "Revived ${core.block.localizedName} at (${core.tileX}, ${core.tileY}) for ${formatCost(cost)}.",
        )
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

    private fun menuContent(viewer: Player): String {
        val cooldown = getRemainingCooldown(viewer.team())
        return if (cooldown > Duration.ZERO) {
            "Select a destroyed core to revive.\nCooldown: ${formatDuration(cooldown)} remaining."
        } else {
            "Select a destroyed core to revive."
        }
    }

    private fun renderCore(core: DestroyedCore): String =
        "${core.block.localizedName} (${core.tileX}, ${core.tileY}) - ${formatCost(reviveCost(core.block))}"

    private fun formatCost(cost: List<ItemRequirement>): String =
        cost.joinToString(" + ") { requirement -> "${requirement.amount} ${requirement.item.localizedName}" }

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

    private data class ReviveResult(val success: Boolean, val message: String)

    private companion object {
        private val REVIVE_CORE = key<DestroyedCore>("revive_core")
        const val PAGE_SIZE = 5
        const val DEFAULT_COST_FACTOR = 3000
        const val ATTACK_COST_FACTOR = 1000
        val COOLDOWN = 5.minutes
    }
}
