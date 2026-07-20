// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.chat

import arc.graphics.Color
import com.xpdustry.distributor.api.audience.Audience
import com.xpdustry.distributor.api.audience.PlayerAudience
import com.xpdustry.distributor.api.component.render.ComponentStringBuilder
import com.xpdustry.distributor.api.component.style.ComponentColor
import com.xpdustry.distributor.api.key.StandardKeys
import com.xpdustry.distributor.api.plugin.MindustryPlugin
import com.xpdustry.imperium.common.account.Achievement
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.IMPERIUM_SCOPE
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.dependency.Inject
import com.xpdustry.imperium.common.dependency.Named
import com.xpdustry.imperium.common.misc.BLURPLE
import com.xpdustry.imperium.common.misc.toHexString
import com.xpdustry.imperium.common.user.User
import com.xpdustry.imperium.common.user.UserManager
import com.xpdustry.imperium.mindustry.bridge.DiscordAudience
import com.xpdustry.imperium.mindustry.misc.Entities
import com.xpdustry.imperium.mindustry.misc.PlayerMap
import com.xpdustry.imperium.mindustry.misc.runMindustryThread
import com.xpdustry.imperium.mindustry.misc.sessionKey
import com.xpdustry.imperium.mindustry.misc.toHexString
import com.xpdustry.imperium.mindustry.store.DataStoreService
import java.text.DecimalFormat
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import mindustry.gen.Iconc
import mindustry.graphics.Pal

@Inject
class MindustryAudienceFormatter(
    plugin: MindustryPlugin,
    private val config: ImperiumConfig,
    private val store: DataStoreService,
    private val users: UserManager,
    @Named(IMPERIUM_SCOPE) private val scope: CoroutineScope,
) : ImperiumApplication.Listener {
    private val ranks = PlayerMap<Rank>(plugin)
    private val hours = PlayerMap<Int>(plugin)
    private val hidden = PlayerMap<Boolean>(plugin)
    private val rainbow = PlayerMap<Boolean>(plugin)
    private var refreshJob: Job? = null

    override fun onImperiumInit() {
        refreshJob =
            scope.launch {
                while (isActive) {
                    refreshPlayerData()
                    delay(2.seconds)
                }
            }
    }

    override fun onImperiumExit() {
        refreshJob?.cancel()
    }

    fun formatName(audience: Audience): String =
        when (audience) {
            is PlayerAudience -> formatPlayerName(audience)
            else -> decoratedName(audience)
        }

    fun formatMessage(sender: Audience, message: String, template: MindustryMessageTemplate): String =
        when (template) {
            MindustryMessageTemplate.CHAT -> formatChatMessage(sender, message)
            MindustryMessageTemplate.TEAM -> "[${teamColor(sender)}]<T> ${formatChatMessage(sender, message)}"
            MindustryMessageTemplate.SAY ->
                "[${config.mindustry.color.toHexString()}]<${Iconc.infoCircle}> ${config.server.name} [accent]>[white] $message"
            MindustryMessageTemplate.WHISPER -> "[gray]<T> ${formatChatMessage(sender, message)}"
        }

    private suspend fun refreshPlayerData() {
        Entities.getPlayersAsync().forEach { player ->
            val stored = store.selectBySessionKey(player.sessionKey)
            val undercover = users.getSetting(player.uuid(), User.Setting.UNDERCOVER)
            val rainbowName =
                users.getSetting(player.uuid(), User.Setting.RAINBOW_NAME) &&
                    (stored?.let { Achievement.SUPPORTER in it.achievements } == true)
            runMindustryThread {
                hidden[player] = undercover
                rainbow[player] = rainbowName
                if (stored == null) {
                    hours.remove(player)
                    ranks.remove(player)
                } else {
                    hours[player] = stored.account.playtime.inWholeHours.toInt()
                    ranks[player] = stored.account.rank
                }
            }
        }
    }

    private fun formatChatMessage(sender: Audience, message: String): String = buildString {
        if (sender is DiscordAudience) {
            append("[")
            append(BLURPLE.toHexString())
            append("]<")
            append(Iconc.discord)
            append("> ")
        }
        if (sender is PlayerAudience) {
            append(formatPlayerName(sender))
        } else {
            append(formatNameTag(formatName(sender), formatHours(sender), rankColor(sender), audienceColor(sender)))
        }
        append(" [accent]>[white] ")
        append(message)
    }

    private fun formatHours(audience: Audience): String =
        when (audience) {
            is DiscordAudience -> audience.hours?.let(CHAOTIC_HOUR_FORMAT::format) ?: ""
            is PlayerAudience -> {
                if (hidden[audience.player] == true) {
                    ""
                } else {
                    hours[audience.player]?.let(CHAOTIC_HOUR_FORMAT::format) ?: ""
                }
            }
            else -> ""
        }

    private fun rankColor(audience: Audience): String =
        when (audience) {
            is DiscordAudience -> audience.rank.toColor().toHexString()
            is PlayerAudience -> {
                if (hidden[audience.player] == true) {
                    Rank.EVERYONE.toColor().toHexString()
                } else {
                    (ranks[audience.player] ?: Rank.EVERYONE).toColor().toHexString()
                }
            }
            else -> Rank.EVERYONE.toColor().toHexString()
        }

    private fun audienceColor(audience: Audience): String =
        audience.metadata[StandardKeys.COLOR]?.toHexString() ?: Color.white.toHexString()

    private fun teamColor(audience: Audience): String =
        when (audience) {
            is PlayerAudience -> audience.player.team().color.toHexString()
            else -> audienceColor(audience)
        }

    private fun formatPlayerName(audience: PlayerAudience): String {
        val component = audience.metadata[StandardKeys.DECORATED_NAME]
        val name =
            if (component == null) {
                audience.metadata[StandardKeys.NAME] ?: ""
            } else if (rainbow[audience.player] == true && hidden[audience.player] != true) {
                val plain = ComponentStringBuilder.plain(audience.metadata).append(component).toString()
                val initial = (((System.currentTimeMillis() / 1000L) % 60) / 60F) * 360F
                val color = Color().a(1F)
                buildString {
                    for ((index, char) in plain.withIndex()) {
                        color.fromHsv(initial + (index * 8F), 0.55F, 0.9F)
                        append("[#")
                        append(color)
                        append("]")
                        append(char)
                    }
                }
            } else {
                ComponentStringBuilder.mindustry(audience.metadata).append(component).toString()
            }
        return formatNameTag(name, formatHours(audience), rankColor(audience), audienceColor(audience))
    }

    private fun decoratedName(audience: Audience): String =
        audience.metadata[StandardKeys.DECORATED_NAME]?.let {
            ComponentStringBuilder.mindustry(audience.metadata).append(it).toString()
        } ?: audience.metadata[StandardKeys.NAME] ?: ""

    private fun Rank.toColor() =
        when (this) {
            Rank.EVERYONE -> Color.lightGray
            Rank.VERIFIED -> Pal.accent
            Rank.OVERSEER -> Color.green
            Rank.MODERATOR -> Color.royal
            Rank.ADMIN,
            Rank.OWNER -> Color.scarlet
        }

    private fun ComponentColor.toHexString(): String = String.format("#%06X", rgb and 0xFFFFFF)

    private fun formatNameTag(name: String, hours: String, rankColor: String, audienceColor: String): String =
        "[$rankColor]<[white]$hours[$rankColor]> [$audienceColor]$name"

    companion object {
        val CHAOTIC_HOUR_FORMAT = DecimalFormat("000")
    }
}
