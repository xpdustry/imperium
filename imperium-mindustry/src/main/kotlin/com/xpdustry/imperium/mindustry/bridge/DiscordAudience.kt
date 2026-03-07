// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.bridge

import com.xpdustry.distributor.api.audience.Audience
import com.xpdustry.distributor.api.component.TextComponent.text
import com.xpdustry.distributor.api.component.style.ComponentColor
import com.xpdustry.distributor.api.key.DynamicKeyContainer
import com.xpdustry.distributor.api.key.StandardKeys
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.misc.BLURPLE
import java.util.Locale

class DiscordAudience(name: String, val rank: Rank, val hours: Int?, language: Locale) : Audience {
    private val metadata: DynamicKeyContainer =
        DynamicKeyContainer.builder()
            .putConstant(StandardKeys.DECORATED_NAME, text(name))
            .putConstant(StandardKeys.NAME, name)
            .putConstant(StandardKeys.COLOR, ComponentColor.from(BLURPLE))
            .putConstant(StandardKeys.LOCALE, language)
            .build()

    override fun getMetadata() = metadata
}
