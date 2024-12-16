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
