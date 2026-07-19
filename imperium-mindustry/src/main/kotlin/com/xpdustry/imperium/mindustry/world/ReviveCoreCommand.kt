// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.world

import com.xpdustry.distributor.api.annotation.EventHandler
import com.xpdustry.distributor.api.command.CommandSender
import com.xpdustry.distributor.api.command.cloud.specifier.AllTeams
import com.xpdustry.distributor.api.component.TextComponent.text
import com.xpdustry.distributor.api.gui.Action
import com.xpdustry.distributor.api.gui.BiAction
import com.xpdustry.distributor.api.gui.menu.ListTransformer
import com.xpdustry.distributor.api.gui.menu.MenuManager
import com.xpdustry.distributor.api.gui.menu.MenuOption
import com.xpdustry.distributor.api.plugin.MindustryPlugin
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.IMPERIUM_SCOPE
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.content.MindustryGamemode
import com.xpdustry.imperium.common.dependency.Inject
import com.xpdustry.imperium.common.dependency.Named
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.command.annotation.Scope
import com.xpdustry.imperium.mindustry.command.annotation.ServerSide
import com.xpdustry.imperium.mindustry.command.vote.AbstractVoteCommand
import com.xpdustry.imperium.mindustry.command.vote.Vote
import com.xpdustry.imperium.mindustry.command.vote.VoteManager
import com.xpdustry.imperium.mindustry.game.MenuToPlayEvent
import com.xpdustry.imperium.mindustry.misc.Entities
import com.xpdustry.imperium.mindustry.misc.component1
import com.xpdustry.imperium.mindustry.misc.component2
import com.xpdustry.imperium.mindustry.misc.component3
import com.xpdustry.imperium.mindustry.misc.runMindustryThread
import com.xpdustry.imperium.mindustry.security.AfkManager
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CoroutineScope
import mindustry.Vars
import mindustry.content.Blocks
import mindustry.content.Items
import mindustry.content.Planets
import mindustry.game.EventType
import mindustry.game.Gamemode
import mindustry.game.Team
import mindustry.gen.Call
import mindustry.gen.Player
import mindustry.type.ItemStack
import mindustry.world.blocks.storage.CoreBlock
import org.incendo.cloud.annotation.specifier.Range

@Inject
class ReviveCoreCommand(afk: AfkManager, plugin: MindustryPlugin, @Named(IMPERIUM_SCOPE) scope: CoroutineScope) :
    AbstractVoteCommand<ReviveCoreCommand.ReviveData>(plugin, "revive", afk, 1.minutes, scope),
    ImperiumApplication.Listener {

    private val destroyedCores = linkedMapOf<TilePosition, DestroyedCore>()
    private val reviveCooldowns = mutableMapOf<Team, Long>()

    private val menu =
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
                        BiAction.from<DestroyedCore>(Action.hideAll()).then { window, core ->
                            onReviveVoteStart(window.viewer, core)
                        }
                    )
            )
            addTransformer { (pane) -> pane.grid.addRow(MenuOption.of("[lightgray]Close", Action.hideAll())) }
        }

    @EventHandler
    internal fun onBlockDestroyEvent(event: EventType.BlockDestroyEvent) {
        if (Vars.state.rules.coreCapture) return
        val building = event.tile.build as? CoreBlock.CoreBuild ?: return
        val position = TilePosition(building.tileX(), building.tileY())
        destroyedCores[position] = DestroyedCore(position.x, position.y, building.block as CoreBlock)
    }

    @EventHandler
    internal fun onGameOverEvent(event: EventType.GameOverEvent) {
        manager.session?.failure()
    }

    @EventHandler
    internal fun onMenuToPlayEvent(event: MenuToPlayEvent) {
        manager.session?.failure()
        destroyedCores.clear()
        reviveCooldowns.clear()
    }

    @ImperiumCommand(["revivecore|rc"])
    @Scope(MindustryGamemode.SURVIVAL, MindustryGamemode.ATTACK, MindustryGamemode.SURVIVAL_EXPERT)
    @ClientSide
    fun onReviveCoreCommand(sender: CommandSender) {
        if (getDestroyedCores().isEmpty()) {
            sender.reply("No destroyed cores can be revived right now.")
            return
        }
        menu.create(sender.player).show()
    }

    @ImperiumCommand(["revivecore|rc", "y"])
    @Scope(MindustryGamemode.SURVIVAL, MindustryGamemode.ATTACK, MindustryGamemode.SURVIVAL_EXPERT)
    @ClientSide
    fun onReviveCoreYesCommand(sender: CommandSender) {
        onPlayerVote(sender.player, manager.session, Vote.YES)
    }

    @ImperiumCommand(["revivecore|rc", "n"])
    @Scope(MindustryGamemode.SURVIVAL, MindustryGamemode.ATTACK, MindustryGamemode.SURVIVAL_EXPERT)
    @ClientSide
    fun onReviveCoreNoCommand(sender: CommandSender) {
        onPlayerVote(sender.player, manager.session, Vote.NO)
    }

    @ImperiumCommand(["revivecore|rc", "cancel|c"], Rank.OVERSEER)
    @Scope(MindustryGamemode.SURVIVAL, MindustryGamemode.ATTACK, MindustryGamemode.SURVIVAL_EXPERT)
    @ClientSide
    fun onReviveCoreCancelCommand(sender: CommandSender) {
        onPlayerCancel(sender.player, manager.session)
    }

    @ImperiumCommand(["revivecore|rc", "force|f"], Rank.OVERSEER)
    @Scope(MindustryGamemode.SURVIVAL, MindustryGamemode.ATTACK, MindustryGamemode.SURVIVAL_EXPERT)
    @ClientSide
    fun onReviveCoreForceCommand(sender: CommandSender) {
        onPlayerForceSuccess(sender.player, manager.session)
    }

    @ImperiumCommand(["revivecore|rc"])
    @ServerSide
    fun onServerReviveCoreCommand(sender: CommandSender, @AllTeams team: Team, @Range(min = "0") id: Int) {
        val core = getDestroyedCores().getOrNull(id)
        if (core == null) {
            sender.error("Invalid revive core id. Use `rc list` to view revivable cores.")
            return
        }
        val cost = reviveCost(core.block)
        if (!takeItems(team, cost)) {
            sender.error("Not enough items to revive ${core.block.localizedName}. Need ${formatCost(cost)}.")
            return
        }
        reviveCore(team, core)
        sender.reply("Revived ${core.block.localizedName} at (${core.x}, ${core.y}) for ${formatCost(cost)}.")
    }

    @ImperiumCommand(["revivecore|rc", "list"])
    @ServerSide
    fun onServerReviveCoreListCommand(sender: CommandSender, @Range(min = "1") page: Int = 1) {
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

    private fun onReviveVoteStart(player: Player, core: DestroyedCore) {
        if (manager.session != null) {
            player.sendMessage("[scarlet]There is already a vote for [orange]'revive'[] in progress!")
            return
        }
        if (Vars.state.gameOver) {
            player.sendMessage("[scarlet]You can't start a revive vote when the game is over.")
            return
        }
        if (!isTileAvailable(core)) {
            destroyedCores.remove(TilePosition(core.x, core.y))
            player.sendMessage("[scarlet]That core can no longer be revived.")
            return
        }
        val team = Vars.state.rules.defaultTeam
        val cooldown = getRemainingCooldown(team)
        if (cooldown > Duration.ZERO) {
            player.sendMessage("[scarlet]Your team must wait ${formatDuration(cooldown)} before reviving another core.")
            return
        }
        val cost = reviveCost(core.block)
        if (!takeItems(team, cost)) {
            val items = team.items()
            val missing =
                cost.joinToString(", ") { stack ->
                    "[orange]${stack.amount - items.get(stack.item)}[] ${stack.item.localizedName}"
                }
            player.sendMessage(
                "[scarlet]Not enough items to revive ${core.block.localizedName}. You still need $missing."
            )
            return
        }
        onVoteSessionStart(player, manager.session, ReviveData(core, team, cost))
    }

    override fun getVoteSessionDetails(session: VoteManager.Session<ReviveData>): String {
        val (core, _, cost) = session.objective
        return "Type [accent]/rc y[] to revive [accent]${core.block.localizedName}[] at [red](${core.x}, ${core.y})[].\n[]This will use ${formatCost(cost)}."
    }

    override fun getRequiredVotes(session: VoteManager.Session<ReviveData>, players: Int): Int =
        when (Entities.getPlayers().size) {
            0 -> 0
            1 -> 1
            2,
            3,
            4,
            5 -> 2
            6,
            7,
            8,
            9 -> 3
            else -> 4
        }

    override suspend fun onVoteSessionSuccess(session: VoteManager.Session<ReviveData>) {
        runMindustryThread {
            val (core, team, cost) = session.objective
            if (!isTileAvailable(core)) {
                refundItems(team, cost)
                destroyedCores.remove(TilePosition(core.x, core.y))
                Call.sendMessage(
                    "[scarlet]The location of ${core.block.localizedName} is blocked, the items have been refunded."
                )
                return@runMindustryThread
            }
            reviveCore(team, core)
            Call.sendMessage(
                "[accent]${core.block.localizedName}[] has been revived at (${core.x}, ${core.y}) for ${formatCost(cost)}."
            )
        }
    }

    override suspend fun onVoteSessionFailure(session: VoteManager.Session<ReviveData>) {
        runMindustryThread { refundItems(session.objective.team, session.objective.cost) }
    }

    private fun reviveCore(team: Team, core: DestroyedCore) {
        Call.constructFinish(Vars.world.tile(core.x, core.y), core.block, null, 0, team, false)
        destroyedCores.remove(TilePosition(core.x, core.y))
        reviveCooldowns[team] = System.currentTimeMillis() + COOLDOWN.inWholeMilliseconds
    }

    private fun takeItems(team: Team, cost: List<ItemStack>): Boolean {
        val items = team.items()
        if (cost.any { !items.has(it.item, it.amount) }) {
            return false
        }
        cost.forEach { items.remove(it.item, it.amount) }
        return true
    }

    private fun refundItems(team: Team, cost: List<ItemStack>) {
        cost.forEach { team.items().add(it.item, it.amount) }
    }

    private fun getDestroyedCores(): List<DestroyedCore> {
        destroyedCores.entries.removeIf { !isTileAvailable(it.value) }
        return destroyedCores.values.toList()
    }

    private fun isTileAvailable(core: DestroyedCore): Boolean {
        for (dx in 0 until core.block.size) {
            for (dy in 0 until core.block.size) {
                val tile = Vars.world.tile(core.x + dx + core.block.sizeOffset, core.y + dy + core.block.sizeOffset)
                if (tile?.block() != Blocks.air) return false
            }
        }
        return true
    }

    private fun reviveCost(core: CoreBlock): List<ItemStack> {
        val amount =
            core.size * if (Vars.state.rules.mode() == Gamemode.attack) ATTACK_COST_FACTOR else DEFAULT_COST_FACTOR
        val items =
            when (Vars.state.rules.planet) {
                Planets.erekir -> listOf(Items.carbide, Items.oxide)
                else -> listOf(Items.surgeAlloy, Items.blastCompound)
            }
        return items.map { item -> ItemStack(item, amount) }
    }

    private fun getRemainingCooldown(team: Team): Duration {
        val remaining = (reviveCooldowns[team] ?: 0L) - System.currentTimeMillis()
        return if (remaining <= 0L) Duration.ZERO else remaining.milliseconds
    }

    private fun menuContent(viewer: Player): String {
        val cooldown = getRemainingCooldown(viewer.team())
        return if (cooldown > Duration.ZERO) {
            "Select a destroyed core to revive.\n[scarlet]Cooldown: ${formatDuration(cooldown)} remaining."
        } else {
            "Select a destroyed core to revive."
        }
    }

    private fun renderCore(core: DestroyedCore): String =
        "${core.block.localizedName} (${core.x}, ${core.y}) - ${formatCost(reviveCost(core.block))}"

    private fun formatCost(cost: List<ItemStack>): String =
        cost.joinToString(" + ") { stack -> "${stack.amount} ${stack.item.localizedName}" }

    private fun formatDuration(duration: Duration): String =
        duration.toComponents { minutes, seconds, _ ->
            when {
                minutes > 0 && seconds > 0 -> "${minutes}m ${seconds}s"
                minutes > 0 -> "${minutes}m"
                else -> "${seconds}s"
            }
        }

    private data class TilePosition(val x: Int, val y: Int)

    data class DestroyedCore(val x: Int, val y: Int, val block: CoreBlock)

    data class ReviveData(val core: DestroyedCore, val team: Team, val cost: List<ItemStack>)

    private companion object {
        const val PAGE_SIZE = 5
        const val DEFAULT_COST_FACTOR = 3000
        const val ATTACK_COST_FACTOR = 1000
        val COOLDOWN = 5.minutes
    }
}
