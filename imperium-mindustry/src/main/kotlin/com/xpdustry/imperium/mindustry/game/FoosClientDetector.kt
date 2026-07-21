// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.game

import arc.Core
import arc.util.Log
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
import com.xpdustry.imperium.common.misc.capitalize
import com.xpdustry.imperium.common.security.Identity
import com.xpdustry.imperium.common.security.Punishment
import com.xpdustry.imperium.common.security.PunishmentDuration
import com.xpdustry.imperium.common.security.PunishmentManager
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
import com.xpdustry.imperium.mindustry.translation.player_action_invalid_target
import com.xpdustry.imperium.mindustry.world.ExcavateManager
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import mindustry.Vars
import mindustry.game.EventType
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
    // Don't assign new instance to save memory
    private val fooClients = PlayerMap<Boolean>(plugin)

    override fun onImperiumInit() {
        Vars.netServer.addPacketHandler("fooCheck") { player, _ -> fooClients[player] = true }

        Vars.netServer.addPacketHandler("foosModeration") { playerObject, data ->
            scope.launch {
                val rank = store.selectBySessionKey(playerObject.sessionKey)?.account?.rank ?: Rank.EVERYONE

                if (rank < Rank.OVERSEER) {
                    Core.app.post { playerObject.asAudience.sendMessage(player_action_disallowed()) }
                    return@launch
                }

                Core.app.post {
                    try {
                        val json = Jval.read(data)
                        val targetId = json.getInt("targetID", -1)
                        val target = Groups.player.find { it.id == targetId }
                        val type = json.getString("type", "ban")
                        val ptype = getPunishmentType(type)

                        if (target == null) {
                            playerObject.asAudience.sendMessage(player_action_invalid_target(targetId))
                            return@post
                        }

                        val reason = json.getString("reason", "Moderator Action")
                        val duration = json.getLong("duration", 5.minutes.inWholeMilliseconds).milliseconds

                        scope.launch {
                            executePunishment(
                                verb = type.capitalize(),
                                type = ptype,
                                senderIdentity = playerObject.identity,
                                reply = { msg -> playerObject.sendMessage(msg) },
                                player = target,
                                reason = reason,
                                duration = if (type == "kick") PunishmentDuration.NONE.value else duration,
                            )
                        }
                    } catch (e: Exception) {
                        Log.err("Failed to process foosFreeze packet from ${playerObject.name}", e)
                        playerObject.sendMessage(
                            "Failed to process foosFreeze packet. Ensure you sent the correct data."
                        )
                    }
                }
            }
        }
        Vars.netServer.addPacketHandler("excavateVote") { player, data ->
            val json = Jval.read(data)
            val vote = json.getBool("vote", true)
            val force = json.getBool("force", false)
            excavateManager.excavateVote(player, vote, force)
        }

        Vars.netServer.addPacketHandler("login") { player, data ->
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
        sendPlayerData(event.player, true)
    }

    @EventHandler
    fun onPlayerJoin(event: EventType.PlayerJoin) {
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

    private fun sendPlayerData(player: Player, resend: Boolean = false) {
        scope.launch {
            val json =
                Jval.newObject().apply {
                    put("resend", resend)
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

    private fun getPunishmentType(type: String): Punishment.Type {
        return when (type) {
            "ban" -> Punishment.Type.BAN
            "freeze" -> Punishment.Type.FREEZE
            "mute" -> Punishment.Type.MUTE
            "kick" -> Punishment.Type.KICK
            // Goal is moderation, therefore default to kick so something occurs
            else -> Punishment.Type.KICK
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
}
