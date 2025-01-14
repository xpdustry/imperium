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
import com.xpdustry.distributor.api.Distributor
import com.xpdustry.distributor.api.annotation.EventHandler
import com.xpdustry.distributor.api.audience.PlayerAudience
import com.xpdustry.distributor.api.component.Component
import com.xpdustry.distributor.api.player.MUUID
import com.xpdustry.distributor.api.util.Priority
import com.xpdustry.flex.FlexAPI
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.collection.enumSetOf
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.database.IdentifierCodec
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.message.Messenger
import com.xpdustry.imperium.common.message.consumer
import com.xpdustry.imperium.common.misc.LoggerDelegate
import com.xpdustry.imperium.common.misc.MindustryUUID
import com.xpdustry.imperium.common.misc.stripMindustryColors
import com.xpdustry.imperium.common.misc.toInetAddress
import com.xpdustry.imperium.common.security.Identity
import com.xpdustry.imperium.common.security.Punishment
import com.xpdustry.imperium.common.security.PunishmentManager
import com.xpdustry.imperium.common.security.PunishmentMessage
import com.xpdustry.imperium.common.security.SimpleRateLimiter
import com.xpdustry.imperium.common.user.UserManager
import com.xpdustry.imperium.mindustry.misc.Entities
import com.xpdustry.imperium.mindustry.misc.PlayerMap
import com.xpdustry.imperium.mindustry.misc.asAudience
import com.xpdustry.imperium.mindustry.misc.identity
import com.xpdustry.imperium.mindustry.misc.runMindustryThread
import com.xpdustry.imperium.mindustry.translation.announcement_ban
import com.xpdustry.imperium.mindustry.translation.punishment_message
import com.xpdustry.imperium.mindustry.translation.punishment_message_simple
import com.xpdustry.imperium.mindustry.translation.warning
import java.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.future.future
import kotlinx.coroutines.launch
import mindustry.Vars
import mindustry.content.Blocks
import mindustry.game.EventType
import mindustry.game.EventType.PlayerBanEvent
import mindustry.game.EventType.PlayerIpBanEvent
import mindustry.gen.Player
import mindustry.net.Administration
import mindustry.world.Block
import mindustry.world.Tile
import mindustry.world.blocks.logic.LogicBlock
import mindustry.world.blocks.logic.MessageBlock

class PunishmentListener(instances: InstanceManager) : ImperiumApplication.Listener {
    private val messenger = instances.get<Messenger>()
    private val punishments = instances.get<PunishmentManager>()
    private val users = instances.get<UserManager>()
    private val messageCooldowns = SimpleRateLimiter<MindustryUUID>(1, 3.seconds)
    private val cache = PlayerMap<List<Punishment>>(instances.get())
    private val kicking = PlayerMap<Boolean>(instances.get())
    private val gatekeeper = instances.get<GatekeeperPipeline>()
    private val badWords = instances.get<BadWordDetector>()
    private val badWordsCounter = SimpleRateLimiter<MUUID>(3, 10.minutes)
    private val config = instances.get<ImperiumConfig>()
    private val codec = instances.get<IdentifierCodec>()

    override fun onImperiumInit() {
        messenger.consumer<PunishmentMessage> { message ->
            val punishment = punishments.findById(message.identifier) ?: return@consumer
            val punished = users.findById(punishment.target) ?: return@consumer
            val data = users.findNamesAndAddressesById(punishment.target)
            val targets =
                Entities.getPlayersAsync().filter { player ->
                    val user = users.getByIdentity(player.identity)
                    user.id == punished.id ||
                        user.uuid == punished.uuid ||
                        player.ip().toInetAddress() in data.addresses
                }

            if (punishment.type == Punishment.Type.BAN && message.type == PunishmentMessage.Type.CREATE) {
                runMindustryThread {
                    Events.fire(PlayerIpBanEvent(punished.lastAddress.hostAddress))
                    targets.forEach { target ->
                        Events.fire(PlayerBanEvent(target, target.uuid()))
                        target.asAudience.kick(punishment_message(punishment, codec), Duration.ZERO)
                        logger.info(
                            "{} ({}) has been banned for '{}'",
                            target.plainName(),
                            target.uuid(),
                            punishment.reason,
                        )
                        Distributor.get()
                            .audienceProvider
                            .players
                            .sendMessage(
                                announcement_ban(
                                    target.name.stripMindustryColors(),
                                    punishment.reason,
                                    punishment.duration,
                                )
                            )
                    }
                }
            } else {
                targets.forEach { target ->
                    refreshPunishments(target)
                    if (message.type == PunishmentMessage.Type.CREATE) {
                        target.sendMessageRateLimited(punishment_message(punishment, codec))
                    }
                }
            }
        }

        Vars.netServer.admins.addActionFilter { action ->
            val freeze = cache[action.player]?.firstOrNull { it.type == Punishment.Type.FREEZE && !it.expired }
            if (freeze != null) {
                if (!isFooNetworking(action.block, action.tile)) {
                    action.player.sendMessageRateLimited(punishment_message(freeze, codec))
                }
                return@addActionFilter false
            }
            if (kicking[action.player] == true) {
                if (!isFooNetworking(action.block, action.tile)) {
                    action.player.sendMessageRateLimited(punishment_message_simple(Punishment.Type.FREEZE, "votekick"))
                }
                return@addActionFilter false
            }
            return@addActionFilter true
        }

        Vars.netServer.admins.addActionFilter { action ->
            val mute =
                cache[action.player]?.firstOrNull { it.type == Punishment.Type.MUTE && !it.expired }
                    ?: return@addActionFilter true

            if (
                (action.type == Administration.ActionType.configure && action.config is String) ||
                    (action.type == Administration.ActionType.placeBlock &&
                        (action.block is MessageBlock || action.block is LogicBlock))
            ) {
                action.player.sendMessageRateLimited(punishment_message(mute, codec))
                return@addActionFilter false
            }

            return@addActionFilter true
        }

        FlexAPI.get().messages.register("mute", Priority.HIGH) { ctx ->
            ImperiumScope.MAIN.future {
                if (!ctx.filter) return@future ctx.message
                val player = ctx.sender as? PlayerAudience ?: return@future ctx.message
                val muted = runMindustryThread {
                    cache[player.player]?.firstOrNull { it.type == Punishment.Type.MUTE && !it.expired }
                }
                if (muted != null) {
                    ctx.sender.sendMessage(punishment_message(muted, codec))
                    ""
                } else {
                    ctx.message
                }
            }
        }

        FlexAPI.get().messages.register("bad_word", Priority.HIGH) { ctx ->
            ImperiumScope.MAIN.future {
                if (!ctx.filter) return@future ctx.message
                val player = ctx.sender as? PlayerAudience ?: return@future ctx.message
                val words = badWords.findBadWords(ctx.message, enumSetOf(Category.HATE_SPEECH, Category.SEXUAL))
                if (words.isNotEmpty()) {
                    if (badWordsCounter.incrementAndCheck(MUUID.from(player.player))) {
                        ctx.sender.sendMessage(warning("bad_word", words.toString()))
                    } else {
                        punishments.punish(
                            config.server.identity,
                            users.getByIdentity(player.player.identity).id,
                            "Bad words: $words",
                            Punishment.Type.MUTE,
                            1.hours,
                        )
                    }
                    ""
                } else {
                    ctx.message
                }
            }
        }

        gatekeeper.register("punishment", Priority.HIGH) { ctx ->
            val punishment =
                punishments
                    .findAllByIdentity(Identity.Mindustry("unknown", ctx.uuid, ctx.usid, ctx.address))
                    .filter { !it.expired && it.type == Punishment.Type.BAN }
                    .toList()
                    .maxByOrNull { it.creation }
            if (punishment == null) {
                GatekeeperResult.Success
            } else {
                GatekeeperResult.Failure(punishment_message(punishment, codec))
            }
        }
    }

    @EventHandler
    fun onVotekickEvent(event: VotekickEvent) {
        kicking[event.target] =
            when (event.type) {
                VotekickEvent.Type.START -> true
                VotekickEvent.Type.CLOSE -> false
            }
    }

    @EventHandler
    fun onPlayerJoin(event: EventType.PlayerJoin) = ImperiumScope.MAIN.launch { refreshPunishments(event.player) }

    private fun Player.sendMessageRateLimited(message: Component) {
        if (messageCooldowns.incrementAndCheck(uuid())) {
            asAudience.sendMessage(message)
        }
    }

    private suspend fun refreshPunishments(player: Player) {
        val result =
            punishments
                .findAllByIdentity(player.identity)
                .filter { !it.expired && it.type != Punishment.Type.BAN }
                .sortedByDescending { it.duration }
        runMindustryThread { cache[player] = result }
    }

    private fun isFooNetworking(block: Block?, tile: Tile?) =
        block == Blocks.microProcessor &&
            tile != null &&
            (tile.x.toInt() == 0 || tile.x.toInt() == Vars.world.width() - 1) &&
            (tile.y.toInt() == 0 || tile.y.toInt() == Vars.world.height() - 1)

    companion object {
        private val logger by LoggerDelegate()
    }
}
