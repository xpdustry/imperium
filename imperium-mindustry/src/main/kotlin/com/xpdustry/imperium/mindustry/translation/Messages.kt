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
@file:Suppress("FunctionName")

package com.xpdustry.imperium.mindustry.translation

import arc.graphics.Color
import com.xpdustry.distributor.api.component.Component
import com.xpdustry.distributor.api.component.ListComponent.components
import com.xpdustry.distributor.api.component.TextComponent.newline
import com.xpdustry.distributor.api.component.TextComponent.text
import com.xpdustry.distributor.api.component.TranslatableComponent.translatable
import com.xpdustry.distributor.api.component.style.ComponentColor
import com.xpdustry.distributor.api.component.style.ComponentColor.GREEN
import com.xpdustry.distributor.api.component.style.ComponentColor.WHITE
import com.xpdustry.distributor.api.translation.TranslationArguments
import com.xpdustry.imperium.common.misc.DISCORD_INVITATION_LINK
import com.xpdustry.imperium.common.security.Punishment
import com.xpdustry.imperium.common.time.truncatedTo
import com.xpdustry.imperium.common.user.User
import com.xpdustry.imperium.mindustry.component.duration
import java.time.temporal.ChronoUnit

private val SCARLET = ComponentColor.from(Color.scarlet)
private val ORANGE = ComponentColor.from(Color.orange)
private val GRAY = ComponentColor.from(Color.gray)
private val LIGHT_GRAY = ComponentColor.from(Color.lightGray)

private const val MESSAGE_PREFIX = "imperium.messages"

fun punishment_message_simple(type: Punishment.Type, reason: String): Component =
    components()
        .setTextColor(SCARLET)
        .append(
            translatable()
                .setKey("$MESSAGE_PREFIX.punishment.message.header")
                .setParameters(
                    TranslationArguments.array(
                        translatable(
                            "$MESSAGE_PREFIX.punishment.type.${type.name.lowercase()}.verb",
                            ORANGE),
                        text(reason, ORANGE))))
        .append(newline())
        .append(
            translatable("$MESSAGE_PREFIX.punishment.type.${type.name.lowercase()}.details", WHITE))
        .build()

fun punishment_message(punishment: Punishment): Component =
    components()
        .setTextColor(SCARLET)
        .append(punishment_message_simple(punishment.type, punishment.reason))
        .append(newline())
        .append(newline())
        .append(
            translatable()
                .setTextColor(ComponentColor.ACCENT)
                .setKey("$MESSAGE_PREFIX.punishment.message.appeal")
                .setParameters(
                    TranslationArguments.array(
                        text(DISCORD_INVITATION_LINK.toString(), ComponentColor.CYAN))))
        .append(newline())
        .append(
            translatable().setTextColor(WHITE).apply {
                val remaining = punishment.remaining
                if (remaining != null) {
                    setKey("$MESSAGE_PREFIX.punishment.expiration")
                    setParameters(
                        TranslationArguments.array(
                            duration(
                                remaining.truncatedTo(ChronoUnit.MINUTES), ComponentColor.ACCENT)))
                } else {
                    setKey("$MESSAGE_PREFIX.punishment.expiration.permanent")
                }
            })
        .append(newline())
        .append(newline())
        .append(
            translatable()
                .setTextColor(GRAY)
                .setKey("$MESSAGE_PREFIX.punishment.message.footer")
                .setParameters(
                    TranslationArguments.array(text(punishment.snowflake.toString(), LIGHT_GRAY))))
        .build()

fun status(status: Boolean): Component =
    translatable(
        "imperium.status.${if (status) "enabled" else "disabled"}", if (status) GREEN else SCARLET)

fun gui_user_settings_title(): Component = translatable("imperium.gui.user-settings.title")

fun gui_user_settings_description(): Component =
    translatable("imperium.gui.user-settings.description")

fun gui_user_settings_entry(setting: User.Setting, value: Boolean): Component =
    components()
        .append(text(setting.name.lowercase().replace('_', '-'), WHITE))
        .append(text(": "))
        .append(status(value))
        .build()

fun gui_close(): Component = translatable("imperium.gui.close")

fun user_setting_description(setting: User.Setting): Component =
    translatable("imperium.user-setting.${setting.name.lowercase().replace('_', '-')}.description")
