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
import com.xpdustry.distributor.api.Distributor
import com.xpdustry.distributor.api.component.Component
import com.xpdustry.distributor.api.component.ListComponent.components
import com.xpdustry.distributor.api.component.NumberComponent.number
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
import com.xpdustry.distributor.api.component.style.TextStyle
import com.xpdustry.distributor.api.translation.TranslationArguments
import com.xpdustry.imperium.common.account.Achievement
import com.xpdustry.imperium.common.content.MindustryMap
import com.xpdustry.imperium.common.database.IdentifierCodec
import com.xpdustry.imperium.common.misc.DISCORD_INVITATION_LINK
import com.xpdustry.imperium.common.security.Punishment
import com.xpdustry.imperium.common.security.ReportMessage
import com.xpdustry.imperium.common.time.truncatedTo
import com.xpdustry.imperium.common.user.User
import com.xpdustry.imperium.mindustry.component.duration
import com.xpdustry.imperium.mindustry.game.Tip
import com.xpdustry.imperium.mindustry.security.MindustryRules
import java.time.temporal.ChronoUnit
import kotlin.time.Duration
import mindustry.game.Team
import mindustry.gen.Iconc
import mindustry.gen.Player
import mindustry.net.Administration.Config
import mindustry.type.UnitType
import mindustry.world.Block

val SCARLET: ComponentColor = from(Color.scarlet)
val ORANGE: ComponentColor = from(Color.orange)
val GRAY: ComponentColor = from(Color.gray)
val LIGHT_GRAY: ComponentColor = from(Color.lightGray)
val BLURPLE: ComponentColor = from(com.xpdustry.imperium.common.misc.BLURPLE)
val ROYAL: ComponentColor = from(Color.royal)
val LIME: ComponentColor = from(Color.lime)

fun punishment_type_verb(type: Punishment.Type): Component =
    translatable("imperium.messages.punishment.type.${type.name.lowercase()}.verb", ORANGE)

fun punishment_message_simple(type: Punishment.Type, reason: String): Component =
    components(
        SCARLET,
        translatable(
            "imperium.messages.punishment.message.header",
            TranslationArguments.array(punishment_type_verb(type), text(reason, ORANGE)),
        ),
        newline(),
        translatable("imperium.messages.punishment.type.${type.name.lowercase()}.details", WHITE),
    )

fun punishment_message(punishment: Punishment, codec: IdentifierCodec): Component =
    components(
        SCARLET,
        punishment_message_simple(punishment.type, punishment.reason),
        newline(),
        newline(),
        translatable(
            "imperium.messages.punishment.message.appeal",
            TranslationArguments.array(text(DISCORD_INVITATION_LINK.toString(), CYAN)),
            ACCENT,
        ),
        translatable()
            .apply {
                setTextStyle(TextStyle.of(WHITE))
                val remaining = punishment.remaining
                if (remaining != null) {
                    setKey("imperium.messages.punishment.expiration")
                    setParameters(
                        TranslationArguments.array(duration(remaining.truncatedTo(ChronoUnit.MINUTES), ACCENT))
                    )
                } else {
                    setKey("imperium.messages.punishment.expiration.permanent")
                }
            }
            .build(),
        newline(),
        newline(),
        translatable(
            "imperium.messages.punishment.message.footer",
            TranslationArguments.array(text(codec.encode(punishment.id), LIGHT_GRAY)),
            GRAY,
        ),
    )

fun warning(kind: String, vararg arguments: String): Component =
    components(
        text(">>> ", SCARLET),
        translatable("imperium.messages.warning", ORANGE),
        text(": ", ORANGE),
        translatable(
            "imperium.messages.warning.$kind",
            TranslationArguments.array(
                listOf(punishment_type_verb(Punishment.Type.MUTE)) + arguments.map { text(it, ORANGE) }
            ),
            WHITE,
        ),
    )

fun status(status: Boolean): Component =
    translatable("imperium.status.${if (status) "enabled" else "disabled"}", if (status) GREEN else SCARLET)

fun gui_user_settings_title(): Component = translatable("imperium.gui.user-settings.title")

fun gui_user_settings_description(): Component = translatable("imperium.gui.user-settings.description")

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
                    Distributor.get().mindustryComponentDecoder.decode(Config.serverName.string())
                ),
            )
        )
        .append(newline())
        .append(translatable("imperium.gui.welcome.content.body"))
        .append(newline())
        .append(newline())
        .append(translatable("imperium.gui.welcome.content.footer", GRAY))
        .build()

fun gui_welcome_button_rules(): Component =
    components(ACCENT, text(Iconc.bookOpen.toString()), space(), translatable("imperium.gui.welcome.button.rules"))

fun gui_welcome_button_discord(): Component = text(Iconc.discord + " Discord", BLURPLE)

fun gui_welcome_button_changelog(): Component =
    components(GREEN, text(Iconc.book), space(), translatable("imperium.gui.welcome.button.changelog"))

fun gui_rules_title(): Component = translatable("imperium.gui.rules.title")

fun gui_rules_content(): Component =
    components()
        .append(
            components(
                SCARLET,
                text(Iconc.warning.toString()),
                space(),
                translatable("imperium.gui.rules.content.header"),
            )
        )
        .apply {
            for (rule in MindustryRules.entries) {
                append(
                    newline(),
                    newline(),
                    text("» ", ROYAL),
                    translatable("imperium.rule.${rule.name.lowercase()}.title", ACCENT),
                    newline(),
                    translatable("imperium.rule.${rule.name.lowercase()}.description"),
                    newline(),
                    components(
                        LIGHT_GRAY,
                        translatable("imperium.gui.rules.content.example"),
                        space(),
                        translatable("imperium.rule.${rule.name.lowercase()}.example"),
                    ),
                )
            }
        }
        .build()

fun gui_report_title(page: Int, pages: Int): Component =
    translatable("imperium.gui.report.title", TranslationArguments.array(page, pages))

fun gui_report_content_player(): Component = translatable("imperium.gui.report.content.player")

fun gui_report_content_reason(): Component = translatable("imperium.gui.report.content.reason")

fun gui_report_content_confirm(player: Player, reason: ReportMessage.Reason): Component =
    translatable(
        "imperium.gui.report.content.confirm",
        TranslationArguments.array(text(player.info.plainLastName(), ACCENT), report_reason(reason)),
    )

fun gui_report_success(): Component = translatable("imperium.gui.report.success")

fun gui_report_failure(): Component = translatable("imperium.gui.report.failure", SCARLET)

fun gui_report_no_players(): Component = translatable("imperium.gui.report.no-players", SCARLET)

fun gui_report_rate_limit(): Component = translatable("imperium.gui.report.rate-limit", SCARLET)

fun gui_rate_map_title(): Component = translatable("imperium.gui.rate-map.title")

fun gui_rate_map_content_score_title(): Component = translatable("imperium.gui.rate-map.content.score.title")

fun gui_rate_map_content_difficulty_title(): Component = translatable("imperium.gui.rate-map.content.difficulty.title")

fun gui_rate_map_success(): Component = translatable("imperium.gui.rate-map.success")

fun gui_rate_map_failure(): Component = translatable("imperium.gui.rate-map.failure", SCARLET)

fun gui_error(): Component = translatable("imperium.gui.error", SCARLET)

fun gui_close(): Component = translatable("imperium.gui.close")

fun gui_back(): Component = translatable("imperium.gui.back", LIGHT_GRAY)

fun gui_submit(): Component = translatable("imperium.gui.submit", ACCENT)

fun user_setting_description(setting: User.Setting): Component =
    translatable("imperium.user-setting.${setting.name.lowercase()}.description")

fun announcement_tip(tip: Tip): Component =
    components(
        WHITE,
        text(">>> ", CYAN),
        translatable("imperium.tip", ACCENT),
        text(": ", ACCENT),
        translatable("imperium.tip.${tip.name.lowercase()}.content"),
        newline(),
        translatable("imperium.tip.${tip.name.lowercase()}.details", LIGHT_GRAY),
    )

fun announcement_ban(target: String, reason: String, duration: Duration): Component =
    translatable(
        "imperium.announcement.ban",
        TranslationArguments.array(text(target, ORANGE), text(reason, ORANGE), duration(duration, ORANGE)),
        SCARLET,
    )

fun announcement_important_block_destroy_attempt(player: Player, block: Block, x: Int, y: Int): Component =
    translatable(
        "imperium.announcement.important-block-destroy-attempt",
        TranslationArguments.array(
            text(player.plainName(), ORANGE),
            translatable(block, ORANGE),
            number(x, ORANGE),
            number(y, ORANGE),
        ),
        SCARLET,
    )

fun announcement_important_block_destroyed(block: Block, x: Int, y: Int): Component =
    translatable(
        "imperium.announcement.important-block-destroyed",
        TranslationArguments.array(translatable(block, ORANGE), number(x, ORANGE), number(y, ORANGE)),
        SCARLET,
    )

fun announcement_dangerous_block_build(player: String, block: Block, x: Int, y: Int): Component =
    translatable(
        "imperium.announcement.dangerous-block-build",
        TranslationArguments.array(
            text(player, ORANGE),
            translatable(block, ORANGE),
            number(x, ORANGE),
            number(y, ORANGE),
        ),
        SCARLET,
    )

fun announcement_impending_explosion_alert(block: Block, x: Int, y: Int): Component =
    translatable(
        "imperium.announcement.impending-explosion-alert",
        TranslationArguments.array(translatable(block, ORANGE), number(x, ORANGE), number(y, ORANGE)),
        SCARLET,
    )

fun command_team_success(team: Team): Component =
    translatable("imperium.command.team.success", TranslationArguments.array(translatable(team, from(team.color))))

fun command_achievements(achievements: List<Achievement>): Component =
    if (achievements.isEmpty()) {
        error("No achievements")
    } else if (achievements.size == 1) {
        components(text(">>> ", CYAN), achievement_description(achievements[0], WHITE))
    } else {
        components()
            .append(translatable("imperium.command.achievements.header", ACCENT))
            .apply {
                achievements.asSequence().sortedWith(compareBy(Enum<Achievement>::name)).forEach {
                    append(newline(), text("> ", CYAN), achievement_name(it, WHITE))
                }
            }
            .build()
    }

fun gatekeeper_failure(kind: String, details: String = "none"): Component =
    translatable("imperium.gatekeeper.failure.$kind", TranslationArguments.array(details), SCARLET)

fun server_restart_delay(reason: String, delay: Duration): Component =
    components(
        SCARLET,
        translatable("imperium.restart.reason.$reason"),
        space(),
        translatable("imperium.restart.trigger.delay", TranslationArguments.array(duration(delay, ACCENT))),
    )

fun server_restart_game_over(reason: String): Component =
    components(
        SCARLET,
        translatable("imperium.restart.reason.$reason"),
        space(),
        translatable("imperium.restart.trigger.game-over"),
    )

fun report_reason(reason: ReportMessage.Reason): Component =
    translatable("imperium.report.reason.${reason.name.lowercase()}", ACCENT)

fun yes(): Component = translatable("imperium.yes", GREEN)

fun no(): Component = translatable("imperium.no", SCARLET)

fun achievement_name(achievement: Achievement, color: ComponentColor): Component =
    translatable("imperium.achievement.${achievement.name.lowercase()}.name", color)

fun achievement_description(achievement: Achievement, color: ComponentColor): Component =
    translatable("imperium.achievement.${achievement.name.lowercase()}.description", color)

fun difficulty_name(difficulty: MindustryMap.Difficulty): Component =
    translatable(
        "imperium.difficulty.${difficulty.name.lowercase()}.name",
        when (difficulty) {
            MindustryMap.Difficulty.EASY -> LIME
            MindustryMap.Difficulty.NORMAL -> ACCENT
            MindustryMap.Difficulty.HARD -> ORANGE
            MindustryMap.Difficulty.EXPERT -> SCARLET
        },
    )

fun selected(component: Component, selected: Boolean): Component =
    if (selected) components(text("> "), component, text(" <")) else component

fun spawned(player: Player, amount: Int, unit: UnitType, team: Team, x: Int, y: Int): Component =
    components(
        WHITE,
        text(">>> ", CYAN),
        translatable(
            "imperium.spawn-unit.success",
            TranslationArguments.array(
                text(player.info.plainLastName(), ACCENT),
                number(amount, ACCENT),
                translatable(unit, ACCENT),
                translatable(team, from(team.color)),
                number(x, ACCENT),
                number(y, ACCENT),
            ),
        ),
    )

fun marked_griefer_block(player: Player): Component =
    components(
        WHITE,
        text(">>> ", CYAN),
        translatable("imperium.marked.block", TranslationArguments.array(text(player.info.plainLastName(), SCARLET))),
    )

fun marked_griefer_unit(player: Player): Component =
    components(
        WHITE,
        text(">>> ", CYAN),
        translatable("imperium.marked.unit", TranslationArguments.array(text(player.info.plainLastName(), SCARLET))),
    )
