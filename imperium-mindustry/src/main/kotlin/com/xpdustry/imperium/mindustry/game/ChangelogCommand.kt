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
package com.xpdustry.imperium.mindustry.game

import com.xpdustry.distributor.api.command.CommandSender
import com.xpdustry.distributor.api.component.Component
import com.xpdustry.distributor.api.component.ListComponent.components
import com.xpdustry.distributor.api.component.TextComponent.newline
import com.xpdustry.distributor.api.component.TextComponent.text
import com.xpdustry.distributor.api.component.style.ComponentColor
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.version.ImperiumVersion
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.command.annotation.ServerSide
import com.xpdustry.imperium.mindustry.translation.GRAY
import java.io.Reader

class ChangelogCommand(instances: InstanceManager) : ImperiumApplication.Listener {

    private val version = instances.get<ImperiumVersion>()

    private val changelog: Component

    init {
        val features = ArrayList<String>()
        val bugfixes = ArrayList<String>()
        javaClass.classLoader.getResourceAsStream("imperium-changelog.txt")!!.reader().use(Reader::readLines).forEach {
            entry ->
            val (verb, scope, message) = entry.split('/', limit = 3)
            if (scope == "common" || scope == "mindustry") {
                when (verb) {
                    "feat" -> features += message
                    "fix" -> bugfixes += message
                }
            }
        }

        val builder = components()
        val none = text("None", GRAY)

        builder.append(text("Changelog for Imperium v$version", ComponentColor.GREEN))
        builder.append(newline(), newline())
        builder.append(text("Features and Changes", ComponentColor.ACCENT))
        builder.append(newline(), newline())
        if (features.isNotEmpty()) {
            features.forEach { feature -> builder.append(text(" - "), text(feature), newline(), newline()) }
        } else {
            builder.append(none, newline(), newline())
        }
        builder.append(text("Bugfixes", ComponentColor.ACCENT))
        builder.append(newline(), newline())
        if (bugfixes.isNotEmpty()) {
            bugfixes.forEach { fix -> builder.append(text(" - "), text(fix), newline(), newline()) }
        } else {
            builder.append(none)
        }
        changelog = builder.build()
    }

    @ImperiumCommand(["changelog"])
    @ClientSide
    @ServerSide
    fun onChangelogCommand(sender: CommandSender) {
        if (sender.isPlayer) {
            sender.audience.sendAnnouncement(changelog)
        } else {
            sender.audience.sendMessage(changelog)
        }
    }
}
