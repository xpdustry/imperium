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
package com.xpdustry.imperium.mindustry.security

import arc.Events
import com.xpdustry.distributor.api.annotation.EventHandler
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.message.Messenger
import com.xpdustry.imperium.common.message.consumer
import com.xpdustry.imperium.common.misc.LoggerDelegate
import com.xpdustry.imperium.common.misc.MindustryUUID
import com.xpdustry.imperium.common.misc.stripMindustryColors
import com.xpdustry.imperium.common.misc.toInetAddress
import com.xpdustry.imperium.common.security.Punishment
import com.xpdustry.imperium.common.security.PunishmentManager
import com.xpdustry.imperium.common.security.PunishmentMessage
import com.xpdustry.imperium.common.security.SimpleRateLimiter
import com.xpdustry.imperium.common.snowflake.Snowflake
import com.xpdustry.imperium.common.time.TimeRenderer
import com.xpdustry.imperium.common.user.UserManager
import com.xpdustry.imperium.mindustry.misc.Entities
import com.xpdustry.imperium.mindustry.misc.PlayerMap
import com.xpdustry.imperium.mindustry.misc.identity
import com.xpdustry.imperium.mindustry.misc.runMindustryThread
import java.time.Instant
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.launch
import mindustry.Vars
import mindustry.game.EventType
import mindustry.game.EventType.PlayerBanEvent
import mindustry.game.EventType.PlayerIpBanEvent
import mindustry.gen.Call
import mindustry.gen.Player
import mindustry.net.Administration
import mindustry.world.blocks.logic.LogicBlock
import mindustry.world.blocks.logic.MessageBlock

// TODO PunishmentListener should cache all non-ban punishments of online players
class PunishmentListener(instances: InstanceManager) : ImperiumApplication.Listener {
    private val messenger = instances.get<Messenger>()
    private val punishments = instances.get<PunishmentManager>()
    private val users = instances.get<UserManager>()
    private val freezes = instances.get<FreezeManager>()
    private val messageCooldowns = SimpleRateLimiter<MindustryUUID>(1, 3.seconds)
    private val renderer = instances.get<TimeRenderer>()
    private val mutes = PlayerMap<Mute>(instances.get())

    override fun onImperiumInit() {
        // TODO Properly notify the target when it gets punished by a non-ban punishment
        messenger.consumer<PunishmentMessage> { message ->
            val punishment = punishments.findBySnowflake(message.snowflake) ?: return@consumer
            if (punishment.type != Punishment.Type.BAN) {
                return@consumer
            }
            val user = users.findBySnowflake(punishment.target) ?: return@consumer
            val data = users.findNamesAndAddressesBySnowflake(user.snowflake)
            runMindustryThread {
                Events.fire(PlayerIpBanEvent(user.lastAddress.hostAddress))
                for (player in Entities.getPlayers()) {
                    if (player.ip().toInetAddress() !in data.addresses &&
                        player.uuid() != user.uuid) {
                        continue
                    }
                    Events.fire(PlayerBanEvent(player, player.uuid()))
                    player.kick(
                        """
                        You have been banned for [red]'${punishment.reason}'.
                        You can [accent]appeal[] your ban in our discord server at [cyan]https://discord.xpdustry.com[].
                        Your punishment id is [lightgrey]${punishment.snowflake}[].
                        """
                            .trimIndent(),
                        0,
                    )
                    logger.info(
                        "{} ({}) has been banned for '{}'",
                        player.plainName(),
                        player.uuid(),
                        punishment.reason,
                    )
                    Call.sendMessage(
                        "[scarlet]Player [orange]${player.name.stripMindustryColors()}[] has been banned for [orange]${punishment.reason}[] for [orange]${renderer.renderDuration(punishment.duration)}[].")
                }
            }
        }

        messenger.consumer<PunishmentMessage> { message ->
            val punishment = punishments.findBySnowflake(message.snowflake) ?: return@consumer
            if (punishment.type != Punishment.Type.MUTE) {
                return@consumer
            }
            Entities.getPlayersAsync().forEach { player ->
                ImperiumScope.MAIN.launch {
                    val user = users.getByIdentity(player.identity)
                    if (user.snowflake != punishment.target) return@launch
                    runMindustryThread {
                        when (message.type) {
                            PunishmentMessage.Type.CREATE ->
                                mutes[player] =
                                    Mute(
                                        punishment.snowflake,
                                        punishment.reason,
                                        punishment.expiration)
                            PunishmentMessage.Type.PARDON -> mutes.remove(player)
                        }
                    }
                }
            }
        }

        Vars.netServer.admins.addActionFilter { action ->
            val freeze = freezes.getFreeze(action.player) ?: return@addActionFilter true

            tryNotifyPlayer(
                action.player,
                """
                You are [cyan]Frozen[white]! You can't interact with anything for a while.
                Reason: "${freeze.reason}"
                ${if (freeze.punishment != null) "[lightgray]ID: ${freeze.punishment}" else ""}
                """
                    .trimIndent())

            return@addActionFilter false
        }

        Vars.netServer.admins.addActionFilter { action ->
            val mute = mutes[action.player] ?: return@addActionFilter true
            if (mute.expiration != null && Instant.now() > mute.expiration) {
                mutes.remove(action.player)
                return@addActionFilter true
            }

            if ((action.type == Administration.ActionType.configure && action.config is String) ||
                (action.type == Administration.ActionType.placeBlock &&
                    (action.block is MessageBlock || action.block is LogicBlock))) {
                tryNotifyPlayer(
                    action.player,
                    """
                    You are [cyan]Muted[white]! You can't build nor configure blocks that can display text for a while.
                    Reason: "${mute.reason}"
                    [lightgray]ID: ${mute.punishment}
                    """
                        .trimIndent())
                return@addActionFilter false
            }

            return@addActionFilter true
        }
    }

    @EventHandler
    fun onPlayerJoin(event: EventType.PlayerJoin) =
        ImperiumScope.MAIN.launch {
            val mute =
                punishments
                    .findAllByIdentity(event.player.identity)
                    .filter { it.type == Punishment.Type.MUTE && !it.expired }
                    .maxByOrNull { it.expiration ?: Instant.MIN }
            if (mute != null) {
                runMindustryThread {
                    mutes[event.player] = Mute(mute.snowflake, mute.reason, mute.expiration)
                }
            }
        }

    private fun tryNotifyPlayer(player: Player, message: String) {
        if (messageCooldowns.incrementAndCheck(player.uuid())) {
            player.sendMessage(message)
        }
    }

    data class Mute(val punishment: Snowflake, val reason: String, val expiration: Instant?)

    companion object {
        private val logger by LoggerDelegate()
    }
}
