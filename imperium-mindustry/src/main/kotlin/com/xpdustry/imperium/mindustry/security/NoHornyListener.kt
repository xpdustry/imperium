// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.security

import com.xpdustry.distributor.api.annotation.EventHandler
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.IMPERIUM_SCOPE
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.dependency.Inject
import com.xpdustry.imperium.common.dependency.Named
import com.xpdustry.imperium.common.security.Punishment
import com.xpdustry.imperium.common.security.PunishmentManager
import com.xpdustry.imperium.common.user.UserManager
import com.xpdustry.nohorny.client.ClassificationEvent
import com.xpdustry.nohorny.client.NoHornySetting
import com.xpdustry.nohorny.common.Rating
import kotlin.time.Duration.Companion.days
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Inject
class NoHornyListener(
    private val users: UserManager,
    private val punishments: PunishmentManager,
    private val config: ImperiumConfig,
    @Named(IMPERIUM_SCOPE) private val scope: CoroutineScope,
) : ImperiumApplication.Listener {

    override fun onImperiumInit() {
        NoHornySetting.DISCORD_WEBHOOK_NAME.set(config.server.displayName)
    }

    @EventHandler
    internal fun onClassification(event: ClassificationEvent) {
        if (config.mindustry.noHornyAutoBan && event.response.rating == Rating.NSFW) {
            scope.launch {
                val user = event.author?.uuid?.let { users.findByUuid(it) } ?: return@launch
                punishments.punish(
                    config.server.identity,
                    user.id,
                    // TODO Ideally, it should be part of a context object, but meh...
                    "Placing NSFW image (${event.response.identifier})",
                    Punishment.Type.BAN,
                    30.days,
                )
            }
        }
    }
}
