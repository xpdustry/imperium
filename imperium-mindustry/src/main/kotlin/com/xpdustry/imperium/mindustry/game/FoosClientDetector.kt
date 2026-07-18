// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.game

import arc.util.Log
import arc.util.serialization.Jval
import com.xpdustry.distributor.api.annotation.EventHandler
import com.xpdustry.distributor.api.plugin.MindustryPlugin
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.IMPERIUM_SCOPE
import com.xpdustry.imperium.common.database.IdentifierCodec
import com.xpdustry.imperium.common.dependency.Inject
import com.xpdustry.imperium.common.dependency.Named
import com.xpdustry.imperium.common.security.Identity
import com.xpdustry.imperium.common.security.Punishment
import com.xpdustry.imperium.common.security.PunishmentManager
import com.xpdustry.imperium.common.user.UserManager
import com.xpdustry.imperium.mindustry.account.PlayerLoginEvent
import com.xpdustry.imperium.mindustry.misc.PlayerMap
import com.xpdustry.imperium.mindustry.misc.asAudience
import com.xpdustry.imperium.mindustry.misc.identity
import com.xpdustry.imperium.mindustry.misc.sessionKey
import com.xpdustry.imperium.mindustry.store.DataStoreService
import com.xpdustry.imperium.mindustry.translation.player_action_disallowed
import com.xpdustry.imperium.mindustry.translation.player_action_invalid_target
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
    @Named(IMPERIUM_SCOPE) private val scope: CoroutineScope,
) : ClientDetector, ImperiumApplication.Listener {
    // Don't assign new instance to save memory
    private val fooClients = PlayerMap<Boolean>(plugin)

    override fun onImperiumInit() {
        Vars.netServer.addPacketHandler("fooCheck") { player, _ -> fooClients[player] = true }

        // TODO: rework to generic packet based moderation, where "type" = Punishment.Type
        Vars.netServer.addPacketHandler("foosFreeze") { playerObject, data ->
            scope.launch {
                val audience = playerObject.asAudience
                val rank = store.selectBySessionKey(playerObject.sessionKey)?.account?.rank ?: Rank.EVERYONE

                if (rank < Rank.OVERSEER) {
                    audience.sendMessage(player_action_disallowed())
                    return@launch
                }

                try {
                    val json = Jval.read(data)

                    val targetId = json.getInt("targetID", -1)
                    val target = Groups.player.find { it.id == targetId }

                    if (target == null) {
                        audience.sendMessage(player_action_invalid_target(targetId))
                        return@launch
                    }

                    val defaultReason = "Moderator Freeze"
                    val reason = json.getString("reason", defaultReason)

                    val durationMs = json.getLong("duration", 5.minutes.inWholeMilliseconds)

                    executePunishment(
                        verb = "Froze",
                        type = Punishment.Type.FREEZE,
                        senderIdentity = playerObject.identity,
                        reply = { msg -> playerObject.sendMessage(msg) },
                        player = target,
                        reason = reason,
                        duration = durationMs.milliseconds,
                    )
                } catch (e: Exception) {
                    Log.err("Failed to process foosFreeze packet from ${playerObject.name}", e)
                    // TODO: should this be translated?
                    playerObject.sendMessage("Failed to process foosFreeze packet. Ensure you sent the correct data.")
                }
            }
        }
    }

    @EventHandler
    fun resendPlayerData(event: PlayerLoginEvent, player: Player) {
        sendPlayerData(player, true)
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

    override fun isFooClient(player: Player) = fooClients[player] == true
}
