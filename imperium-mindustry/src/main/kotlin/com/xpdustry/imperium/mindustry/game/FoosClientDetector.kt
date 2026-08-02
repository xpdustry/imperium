// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.game

import arc.Core
import arc.util.serialization.Jval
import com.xpdustry.distributor.api.Distributor
import com.xpdustry.distributor.api.annotation.EventHandler
import com.xpdustry.distributor.api.plugin.MindustryPlugin
import com.xpdustry.imperium.common.account.AccountResult
import com.xpdustry.imperium.common.account.MindustrySessionService
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.IMPERIUM_SCOPE
import com.xpdustry.imperium.common.database.IdentifierCodec
import com.xpdustry.imperium.common.dependency.Inject
import com.xpdustry.imperium.common.dependency.Named
import com.xpdustry.imperium.common.misc.LoggerDelegate
import com.xpdustry.imperium.common.misc.capitalize
import com.xpdustry.imperium.common.security.Identity
import com.xpdustry.imperium.common.security.Punishment
import com.xpdustry.imperium.common.security.PunishmentDuration
import com.xpdustry.imperium.common.security.PunishmentManager
import com.xpdustry.imperium.common.security.RateLimiter
import com.xpdustry.imperium.common.security.SimpleRateLimiter
import com.xpdustry.imperium.common.string.Password
import com.xpdustry.imperium.common.user.UserManager
import com.xpdustry.imperium.mindustry.account.PlayerLoginEvent
import com.xpdustry.imperium.mindustry.account.gui_login_failure_invalid_credentials
import com.xpdustry.imperium.mindustry.account.gui_login_success
import com.xpdustry.imperium.mindustry.account.handleAccountResult
import com.xpdustry.imperium.mindustry.misc.PlayerMap
import com.xpdustry.imperium.mindustry.misc.asAudience
import com.xpdustry.imperium.mindustry.misc.identity
import com.xpdustry.imperium.mindustry.misc.sessionKey
import com.xpdustry.imperium.mindustry.store.DataStoreService
import com.xpdustry.imperium.mindustry.translation.player_action_disallowed
import com.xpdustry.imperium.mindustry.world.ExcavateManager
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import mindustry.Vars
import mindustry.gen.Call
import mindustry.gen.Groups
import mindustry.gen.Player

interface ClientDetector {
    fun isFooClient(player: Player): Boolean
}

@Inject
class FoosClientDetector(
    private val plugin: MindustryPlugin,
    private val store: DataStoreService,
    private val punishments: PunishmentManager,
    private val users: UserManager,
    private val codec: IdentifierCodec,
    private val excavateManager: ExcavateManager,
    private val sessions: MindustrySessionService,
    @Named(IMPERIUM_SCOPE) private val scope: CoroutineScope,
) : ClientDetector, ImperiumApplication.Listener {
    private val fooClients = PlayerMap<Boolean>(plugin)
    private val loginAttempts = PlayerMap<RateLimiter<Unit>>(plugin)

    override fun onImperiumInit() {
        Vars.netServer.addPacketHandler("fooCheck") { player, _ ->
            fooClients[player] = true
            sendPlayerData(player)
        }

        Vars.netServer.addPacketHandler("foosModeration") { playerObject, data ->
            scope.launch {
                val rank = store.selectBySessionKey(playerObject.sessionKey)?.account?.rank ?: Rank.EVERYONE

                if (rank < Rank.OVERSEER) {
                    Core.app.post { playerObject.asAudience.sendMessage(player_action_disallowed()) }
                    return@launch
                }

                Core.app.post {
                    val packet =
                        try {
                            decodeModerationPacket(data)
                        } catch (e: Exception) {
                            logger.debug("Dropped invalid foosModeration packet from player {}", playerObject.id, e)
                            return@post
                        }
                    val target = Groups.player.find { it.id == packet.targetId }
                    if (target == null) {
                        logger.debug(
                            "Dropped invalid foosModeration packet from player {}: target {} does not exist",
                            playerObject.id,
                            packet.targetId,
                        )
                        return@post
                    }

                    scope.launch {
                        executePunishment(
                            verb = packet.typeName.capitalize(),
                            type = packet.type,
                            senderIdentity = playerObject.identity,
                            reply = { msg -> playerObject.sendMessage(msg) },
                            player = target,
                            reason = packet.reason,
                            duration =
                                if (packet.type == Punishment.Type.KICK) PunishmentDuration.NONE.value
                                else packet.duration,
                        )
                    }
                }
            }
        }
        Vars.netServer.addPacketHandler("excavateVote") { player, data ->
            scope.launch {
                try {
                    val json = Jval.read(data)
                    val vote = json.getBool("vote", true)
                    val force =
                        json.getBool("force", false) &&
                            (store.selectBySessionKey(player.sessionKey)?.account?.rank ?: Rank.EVERYONE) >=
                                Rank.OVERSEER

                    excavateManager.excavateVote(player, vote, force)
                } catch (_: Exception) {}
            }
        }

        Vars.netServer.addPacketHandler("login") { player, data ->
            val limiter = loginAttempts[player] ?: SimpleRateLimiter<Unit>(3, 1.minutes)
            loginAttempts[player] = limiter
            if (!limiter.incrementAndCheck(Unit)) {
                logger.debug("Dropped rate-limited login packet from player {}", player.id)
                return@addPacketHandler
            }
            scope.launch {
                val account = sessions.selectByKey(player.sessionKey)
                if (account != null) return@launch player.sendMessage("You are already logged in.")
                try {
                    val json = Jval.read(data)
                    val username = json.getString("username", "")
                    val password = json.getString("password", "")
                    if (username.isBlank() || password.isBlank())
                        return@launch player.asAudience.sendMessage(gui_login_failure_invalid_credentials())
                    loginPlayer(player, username, password)
                } catch (_: Exception) {
                    player.sendMessage("Login failed. Malformed Packet Data")
                }
            }
        }
    }

    @EventHandler
    fun resendPlayerData(event: PlayerLoginEvent) {
        if (isFooClient(event.player)) sendPlayerData(event.player)
    }

    private suspend fun executePunishment(
        verb: String,
        type: Punishment.Type,
        senderIdentity: Identity,
        reply: (String) -> Unit,
        player: Player,
        reason: String,
        duration: Duration,
    ) {
        val id = punishments.punish(senderIdentity, users.getByIdentity(player.identity).id, reason, type, duration)
        reply("$verb user ${player.name} (${codec.encode(id)}).")
    }

    private fun sendPlayerData(player: Player) {
        scope.launch {
            val json =
                Jval.newObject().apply {
                    put("currentName", player.name)
                    put("currentID", player.id)
                    put(
                        "rank",
                        store.selectBySessionKey(player.sessionKey)?.account?.rank?.ordinal ?: Rank.EVERYONE.ordinal,
                    )
                    // TODO: Add more senders, specifically for tile history
                    // and other player's information to admins
                    // using Jval.newArray()
                }
            Call.clientPacketReliable(player.con, "playerdata", json.toString())
        }
    }

    private suspend fun loginPlayer(player: Player, username: String, password: String) {
        val audience = player.asAudience

        val result = sessions.login(player.sessionKey, username, Password(password))
        when (result) {
            is AccountResult.Success -> {
                audience.sendAnnouncement(gui_login_success())
                Distributor.get().eventBus.post(PlayerLoginEvent(player))
            }
            AccountResult.WrongPassword,
            AccountResult.NotFound -> {
                audience.sendAnnouncement(gui_login_failure_invalid_credentials())
            }
            else -> handleAccountResult(result, player)
        }
    }

    override fun isFooClient(player: Player) = fooClients[player] == true

    private data class ModerationPacket(
        val targetId: Int,
        val typeName: String,
        val type: Punishment.Type,
        val reason: String,
        val duration: Duration,
    )

    private companion object {
        private val logger by LoggerDelegate()
        private val MODERATION_PACKET_FIELDS = setOf("targetID", "type", "reason", "duration")
        private const val MAX_REASON_LENGTH = 256

        private fun decodeModerationPacket(data: String): ModerationPacket {
            val json = Jval.read(data)
            require(json.isObject) { "packet must be an object" }
            require(json.asObject().size == MODERATION_PACKET_FIELDS.size && MODERATION_PACKET_FIELDS.all(json::has)) {
                "packet must contain exactly ${MODERATION_PACKET_FIELDS.joinToString()}"
            }

            val targetValue = json.get("targetID")
            require(targetValue.isNumber && targetValue.asNumber() is Long) { "targetID must be an integer" }
            val targetId = targetValue.asLong()
            require(targetId in Int.MIN_VALUE..Int.MAX_VALUE) { "targetID is outside the integer range" }

            val typeValue = json.get("type")
            require(typeValue.isString) { "type must be a string" }
            val typeName = typeValue.asString()
            val type =
                when (typeName) {
                    "ban" -> Punishment.Type.BAN
                    "freeze" -> Punishment.Type.FREEZE
                    "mute" -> Punishment.Type.MUTE
                    "kick" -> Punishment.Type.KICK
                    else -> throw IllegalArgumentException("unknown punishment type '$typeName'")
                }

            val reasonValue = json.get("reason")
            require(reasonValue.isString) { "reason must be a string" }
            val reason = reasonValue.asString()
            require(reason.isNotBlank()) { "reason must not be blank" }
            require(reason.length <= MAX_REASON_LENGTH) { "reason exceeds $MAX_REASON_LENGTH characters" }

            val durationValue = json.get("duration")
            require(durationValue.isNumber && durationValue.asNumber() is Long) { "duration must be an integer" }
            val duration = durationValue.asLong()
            require(duration >= 0L) { "duration must not be negative" }

            return ModerationPacket(targetId.toInt(), typeName, type, reason, duration.milliseconds)
        }
    }
}
