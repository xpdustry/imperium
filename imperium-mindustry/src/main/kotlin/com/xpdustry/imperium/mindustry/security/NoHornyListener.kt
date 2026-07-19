// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.security

import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.dependency.Inject
import com.xpdustry.nohorny.client.NoHornySetting

@Inject
class NoHornyListener(private val config: ImperiumConfig) : ImperiumApplication.Listener {

    override fun onImperiumInit() {
        NoHornySetting.DISCORD_WEBHOOK_NAME.set(config.server.displayName)
    }
}
