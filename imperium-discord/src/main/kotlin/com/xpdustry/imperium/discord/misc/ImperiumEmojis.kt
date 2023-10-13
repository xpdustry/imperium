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
package com.xpdustry.imperium.discord.misc

import org.javacord.api.entity.emoji.CustomEmoji
import org.javacord.api.entity.emoji.Emoji
import org.javacord.api.entity.emoji.KnownCustomEmoji
import java.util.Optional

object ImperiumEmojis {
    val CHECK_MARK: Emoji = UnicodeEmoji("✔️")
    val CROSS_MARK: Emoji = UnicodeEmoji("❌")
    val DOWN_ARROW: Emoji = UnicodeEmoji("⬇️")
    val PENCIL: Emoji = UnicodeEmoji("✏️")
    val INBOX_TRAY: Emoji = UnicodeEmoji("📥")
    val WASTE_BASKET: Emoji = UnicodeEmoji("🗑️")

    private data class UnicodeEmoji(private val emoji: String) : Emoji {
        override fun getMentionTag() = emoji
        override fun asUnicodeEmoji() = Optional.of(emoji)
        override fun asCustomEmoji() = Optional.empty<CustomEmoji>()
        override fun asKnownCustomEmoji() = Optional.empty<KnownCustomEmoji>()
        override fun isAnimated() = false
    }
}
