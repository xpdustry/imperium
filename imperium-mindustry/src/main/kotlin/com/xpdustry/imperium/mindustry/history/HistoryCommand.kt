// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.history

import arc.graphics.Color
import arc.math.Mathf
import com.xpdustry.distributor.api.annotation.EventHandler
import com.xpdustry.distributor.api.annotation.TaskHandler
import com.xpdustry.distributor.api.command.CommandSender
import com.xpdustry.distributor.api.plugin.MindustryPlugin
import com.xpdustry.distributor.api.scheduler.MindustryTimeUnit
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.dependency.Inject
import com.xpdustry.imperium.common.user.User
import com.xpdustry.imperium.common.user.UserManager
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.command.annotation.ServerSide
import com.xpdustry.imperium.mindustry.misc.Entities
import com.xpdustry.imperium.mindustry.misc.PlayerMap
import com.xpdustry.imperium.mindustry.misc.runMindustryThread
import kotlin.time.Clock
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.launch
import mindustry.Vars
import mindustry.game.EventType
import mindustry.gen.Call
import mindustry.gen.Player
import org.incendo.cloud.annotation.specifier.Range

@Inject
class HistoryCommand(
    private val historian: Historian,
    private val users: UserManager,
    private val config: ImperiumConfig,
    private val historyRenderer: HistoryRenderer,
    plugin: MindustryPlugin,
) : ImperiumApplication.Listener {
    private val taps = PlayerMap<PlayerTap>(plugin)
    private val heatmapViewers = PlayerMap<Boolean>(plugin)

    @TaskHandler(interval = 1L, delay = 1L, unit = MindustryTimeUnit.SECONDS)
    internal fun onHeatmapViewUpdate() {
        if (!Vars.state.isPlaying) return
        for (player in Entities.getPlayers()) {
            if (heatmapViewers[player] == true) {
                for (ox in (-config.mindustry.history.heatMapRadius)..config.mindustry.history.heatMapRadius) {
                    for (oy in (-config.mindustry.history.heatMapRadius)..config.mindustry.history.heatMapRadius) {
                        val x = player.tileX() + ox
                        val y = player.tileY() + oy
                        val entry = historian.getHistory(x, y).lastOrNull() ?: continue
                        val minutes = (Clock.System.now() - entry.timestamp).inWholeMinutes.coerceAtMost(30)
                        val progress = minutes / 30F
                        val color = Color.orange.cpy().lerp(Color.blue, progress)
                        Call.label(
                            player.con(),
                            "[#$color][[ $minutes ]",
                            1F,
                            x.toFloat() * Vars.tilesize,
                            y.toFloat() * Vars.tilesize,
                        )
                    }
                }
            }
        }
    }

    @EventHandler
    internal fun onPlayerTapEvent(event: EventType.TapEvent) =
        ImperiumScope.MAIN.launch {
            if (users.getSetting(event.player.uuid(), User.Setting.DOUBLE_TAP_TILE_LOG)) {
                val last = taps[event.player]
                if (
                    last != null &&
                        (System.currentTimeMillis() - last.timestamp).milliseconds <
                            config.mindustry.history.doubleClickDelay &&
                        Mathf.within(last.x, last.y, event.tile.x.toFloat(), event.tile.y.toFloat(), 2F)
                ) {
                    taps.remove(event.player)
                    onTileHistoryCommand(CommandSender.player(event.player), event.tile.x, event.tile.y)
                } else {
                    taps[event.player] =
                        PlayerTap(event.tile.x.toFloat(), event.tile.y.toFloat(), System.currentTimeMillis())
                }
            }
        }

    @ImperiumCommand(["history", "player"])
    @ClientSide
    @ServerSide
    suspend fun onPlayerHistoryCommand(
        sender: CommandSender,
        player: Player,
        @Range(min = "1", max = "50") limit: Int = 10,
    ) =
        sender.reply(
            historyRenderer.render(
                runMindustryThread { historian.getHistory(player.uuid()).normalize(limit) },
                HistoryActor(player),
            )
        )

    @ImperiumCommand(["history", "tile"])
    @ClientSide
    @ServerSide
    suspend fun onTileHistoryCommand(
        sender: CommandSender,
        @Range(min = "1") x: Short,
        @Range(min = "1") y: Short,
        @Range(min = "1", max = "50") limit: Int = 10,
    ) =
        sender.reply(
            historyRenderer.render(
                runMindustryThread { historian.getHistory(x.toInt(), y.toInt()).normalize(limit) },
                x.toInt(),
                y.toInt(),
            )
        )

    @ImperiumCommand(["inspector"])
    @ClientSide
    fun onHeatmapHistoryCommand(sender: CommandSender) {
        val viewing = !(heatmapViewers[sender.player] ?: false)
        heatmapViewers[sender.player] = viewing
        sender.reply("Heatmap is now ${if (viewing) "enabled" else "disabled"}")
    }

    data class PlayerTap(val x: Float, val y: Float, val timestamp: Long)
}
