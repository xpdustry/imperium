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
import com.xpdustry.distributor.api.DistributorProvider
import com.xpdustry.distributor.api.component.Component
import com.xpdustry.distributor.api.component.ListComponent.components
import com.xpdustry.distributor.api.component.TextComponent.newline
import com.xpdustry.distributor.api.component.TextComponent.space
import com.xpdustry.distributor.api.component.TextComponent.text
import com.xpdustry.distributor.api.component.TranslatableComponent.translatable
import com.xpdustry.distributor.api.component.style.ComponentColor
import com.xpdustry.distributor.api.component.style.ComponentColor.ACCENT
import com.xpdustry.distributor.api.component.style.ComponentColor.CYAN
import com.xpdustry.distributor.api.component.style.ComponentColor.GREEN
import com.xpdustry.distributor.api.component.style.ComponentColor.WHITE
import com.xpdustry.distributor.api.component.style.ComponentColor.from
import com.xpdustry.distributor.api.translation.TranslationArguments
import com.xpdustry.imperium.common.misc.DISCORD_INVITATION_LINK
import com.xpdustry.imperium.common.security.Punishment
import com.xpdustry.imperium.common.time.truncatedTo
import com.xpdustry.imperium.common.user.User
import com.xpdustry.imperium.mindustry.component.duration
import com.xpdustry.imperium.mindustry.game.Tip
import com.xpdustry.imperium.mindustry.security.MindustryRules
import java.time.temporal.ChronoUnit
import kotlin.time.Duration
import mindustry.game.Team
import mindustry.gen.Iconc
import mindustry.net.Administration.Config
import mindustry.world.Block

private val SCARLET = ComponentColor.from(Color.scarlet)
private val ORANGE = ComponentColor.from(Color.orange)
private val GRAY = ComponentColor.from(Color.gray)
private val LIGHT_GRAY = ComponentColor.from(Color.lightGray)
private val BLURPLE = ComponentColor.from(com.xpdustry.imperium.common.misc.BLURPLE)
private val ROYAL = ComponentColor.from(Color.royal)

fun punishment_type_verb(type: Punishment.Type): Component =
    translatable("imperium.messages.punishment.type.${type.name.lowercase()}.verb", ORANGE)

fun punishment_message_simple(type: Punishment.Type, reason: String): Component =
    components()
        .setTextColor(SCARLET)
        .append(
            translatable()
                .setKey("imperium.messages.punishment.message.header")
                .setParameters(
                    TranslationArguments.array(punishment_type_verb(type), text(reason, ORANGE))))
        .append(newline())
        .append(
            translatable(
                "imperium.messages.punishment.type.${type.name.lowercase()}.details", WHITE))
        .build()

fun punishment_message(punishment: Punishment): Component =
    components()
        .setTextColor(SCARLET)
        .append(punishment_message_simple(punishment.type, punishment.reason))
        .append(newline())
        .append(newline())
        .append(
            translatable()
                .setTextColor(ACCENT)
                .setKey("imperium.messages.punishment.message.appeal")
                .setParameters(
                    TranslationArguments.array(text(DISCORD_INVITATION_LINK.toString(), CYAN))))
        .append(newline())
        .append(
            translatable().setTextColor(WHITE).apply {
                val remaining = punishment.remaining
                if (remaining != null) {
                    setKey("imperium.messages.punishment.expiration")
                    setParameters(
                        TranslationArguments.array(
                            duration(remaining.truncatedTo(ChronoUnit.MINUTES), ACCENT)))
                } else {
                    setKey("imperium.messages.punishment.expiration.permanent")
                }
            })
        .append(newline())
        .append(newline())
        .append(
            translatable()
                .setTextColor(GRAY)
                .setKey("imperium.messages.punishment.message.footer")
                .setParameters(
                    TranslationArguments.array(text(punishment.snowflake.toString(), LIGHT_GRAY))))
        .build()

fun warning(kind: String): Component =
    components(
        text(">>> ", SCARLET),
        translatable("imperium.messages.warning", ORANGE),
        text(": ", ORANGE),
        translatable(
            "imperium.messages.warning.$kind",
            TranslationArguments.array(punishment_type_verb(Punishment.Type.MUTE)),
            WHITE))

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

fun gui_welcome_title(): Component = translatable("imperium.gui.welcome.title")

fun gui_welcome_content(): Component =
    components()
        .append(
            translatable(
                "imperium.gui.welcome.content.header",
                TranslationArguments.array(
                    DistributorProvider.get()
                        .mindustryComponentDecoder
                        .decode(Config.serverName.string()))))
        .append(newline())
        .append(translatable("imperium.gui.welcome.content.body"))
        .append(newline())
        .append(newline())
        .append(translatable("imperium.gui.welcome.content.footer", GRAY))
        .build()

fun gui_welcome_button_rules(): Component =
    components()
        .setTextColor(ACCENT)
        .append(text(Iconc.bookOpen.toString()))
        .append(space())
        .append(translatable("imperium.gui.welcome.button.rules"))
        .build()

fun gui_welcome_button_discord(): Component = text(Iconc.discord + " Discord", BLURPLE)

fun gui_rules_title(): Component = translatable("imperium.gui.rules.title")

fun gui_rules_content(): Component =
    components()
        .append(
            components()
                .setTextColor(SCARLET)
                .append(text(Iconc.warning.toString()))
                .append(space())
                .append(translatable("imperium.gui.rules.content.header")))
        .modify {
            for (rule in MindustryRules.entries) {
                it.append(newline())
                it.append(newline())
                it.append(text("» ", ROYAL))
                it.append(translatable("imperium.rule.${rule.name.lowercase()}.title", ACCENT))
                it.append(newline())
                it.append(translatable("imperium.rule.${rule.name.lowercase()}.description"))
                it.append(newline())
                it.append(
                    components()
                        .setTextColor(LIGHT_GRAY)
                        .append(translatable("imperium.gui.rules.content.example"))
                        .append(space())
                        .append(translatable("imperium.rule.${rule.name.lowercase()}.example")))
            }
        }
        .build()

fun gui_close(): Component = translatable("imperium.gui.close")

fun gui_back(): Component = translatable("imperium.gui.back")

fun user_setting_description(setting: User.Setting): Component =
    translatable("imperium.user-setting.${setting.name.lowercase().replace('_', '-')}.description")

fun announcement_tip(tip: Tip): Component =
    components()
        .setTextColor(WHITE)
        .append(text(">>> ", CYAN))
        .append(translatable("imperium.tip", ACCENT))
        .append(text(": ", ACCENT))
        .append(translatable("imperium.tip.${tip.name.lowercase()}.content"))
        .append(newline())
        .append(translatable("imperium.tip.${tip.name.lowercase()}.details", LIGHT_GRAY))
        .build()

fun announcement_ban(target: String, reason: String, duration: Duration): Component =
    translatable(
        "imperium.announcement.ban",
        TranslationArguments.array(
            text(target, ORANGE), text(reason, ORANGE), duration(duration, ORANGE)),
        SCARLET)

fun announcement_power_void_destroyed(x: Int, y: Int): Component =
    translatable(
        "imperium.announcement.power-void-destroyed",
        TranslationArguments.array(text(x.toString(), ORANGE), text(y.toString(), ORANGE)),
        SCARLET)

fun announcement_dangerous_block_build(player: String, block: Block, x: Int, y: Int): Component =
    translatable(
        "imperium.announcement.dangerous-block-build",
        TranslationArguments.array(
            text(player, ORANGE),
            translatable(block, ORANGE),
            text(x.toString(), ORANGE),
            text(y.toString(), ORANGE)),
        SCARLET)

fun command_team_success(team: Team): Component =
    translatable(
        "imperium.command.team.success",
        TranslationArguments.array(translatable(team, from(team.color))))

fun gatekeeper_failure(kind: String): Component =
    translatable("imperium.gatekeeper.failure.$kind", SCARLET)
