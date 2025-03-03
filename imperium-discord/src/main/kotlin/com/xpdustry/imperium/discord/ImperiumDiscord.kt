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
package com.xpdustry.imperium.discord

import com.xpdustry.imperium.common.CommonModule
import com.xpdustry.imperium.common.annotation.AnnotationScanner
import com.xpdustry.imperium.common.factory.ObjectFactory
import com.xpdustry.imperium.common.lifecycle.LifecycleListener
import com.xpdustry.imperium.discord.account.RoleSyncListener
import com.xpdustry.imperium.discord.bridge.MindustryBridgeListener
import com.xpdustry.imperium.discord.commands.AccountCommand
import com.xpdustry.imperium.discord.commands.HistoryCommand
import com.xpdustry.imperium.discord.commands.MapCommand
import com.xpdustry.imperium.discord.commands.MapSearchCommand
import com.xpdustry.imperium.discord.commands.MapSubmitCommand
import com.xpdustry.imperium.discord.commands.ModerationCommand
import com.xpdustry.imperium.discord.commands.PingCommand
import com.xpdustry.imperium.discord.commands.PlayerCommand
import com.xpdustry.imperium.discord.commands.ServerCommand
import com.xpdustry.imperium.discord.commands.VerifyCommand
import com.xpdustry.imperium.discord.commands.WhitelistCommand
import com.xpdustry.imperium.discord.content.MindustryContentListener
import com.xpdustry.imperium.discord.metrics.MetricsListener
import com.xpdustry.imperium.discord.rest.RestListener
import com.xpdustry.imperium.discord.security.PunishmentListener
import com.xpdustry.imperium.discord.security.ReportListener
import org.slf4j.Logger
import org.slf4j.LoggerFactory

private val LOGGER = LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)

fun main() {
    val factory = ObjectFactory.create(CommonModule(), DiscordModule())

    val listeners = mutableListOf<LifecycleListener>()

    factory.initialize()
    for (obj in factory.objects()) {
        if (obj is LifecycleListener) {
            obj.onImperiumInit()
            listeners += obj
        }
    }

    sequenceOf(
            MindustryBridgeListener::class,
            PingCommand::class,
            ServerCommand::class,
            ReportListener::class,
            MapCommand::class,
            VerifyCommand::class,
            ModerationCommand::class,
            PunishmentListener::class,
            RoleSyncListener::class,
            PlayerCommand::class,
            AccountCommand::class,
            WhitelistCommand::class,
            RestListener::class,
            MapSearchCommand::class,
            MapSubmitCommand::class,
            MindustryContentListener::class,
            HistoryCommand::class,
            MetricsListener::class,
        )
        .forEach { listeners += factory.get(it.java).apply { onImperiumInit() } }

    val scanners =
        listOf(
            factory.get(AnnotationScanner::class.java, "slash"),
            factory.get(AnnotationScanner::class.java, "menu"),
            factory.get(AnnotationScanner::class.java, "modal"),
        )

    for (listener in listeners) {
        for (scanner in scanners) {
            scanner.scan(listener)
        }
    }

    for (scanner in scanners) {
        scanner.process()
    }

    LOGGER.info("Imperium loaded.")
}
