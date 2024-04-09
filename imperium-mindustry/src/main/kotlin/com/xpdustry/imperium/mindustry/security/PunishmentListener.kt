/*
 * Imperium, the software collection powering the Xpdustry network.
 * Copyright (C) 2023  Xpdustry
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
import com.xpdustry.imperium.common.application.ImperiumApplication
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
import com.xpdustry.imperium.common.time.TimeRenderer
import com.xpdustry.imperium.common.user.UserManager
import com.xpdustry.imperium.mindustry.misc.Entities
import com.xpdustry.imperium.mindustry.misc.runMindustryThread
import kotlin.time.Duration.Companion.seconds
import mindustry.Vars
import mindustry.game.EventType.PlayerBanEvent
import mindustry.game.EventType.PlayerIpBanEvent
import mindustry.gen.Call

class PunishmentListener(instances: InstanceManager) : ImperiumApplication.Listener {
    private val messenger = instances.get<Messenger>()
    private val punishments = instances.get<PunishmentManager>()
    private val users = instances.get<UserManager>()
    private val freezes = instances.get<FreezeManager>()
    private val freezeMessageCooldowns = SimpleRateLimiter<MindustryUUID>(1, 3.seconds)
    private val renderer = instances.get<TimeRenderer>()

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

        Vars.netServer.admins.addActionFilter { action ->
            val freeze = freezes.getFreeze(action.player) ?: return@addActionFilter true

            if (freezeMessageCooldowns.incrementAndCheck(action.player.uuid())) {
                action.player.sendMessage(
                    buildString {
                        appendLine("You are [cyan]Frozen[white]! You can't interact with anything until a moderator unfreezes you.")
                        appendLine("Reason: \"${freeze.reason}\"")
                        if (freeze.punishment != null) {
                            appendLine("[lightgray]ID: ${freeze.punishment}")
                        }
                    })
            }

            return@addActionFilter false
        }
    }

    companion object {
        private val logger by LoggerDelegate()
    }
}
