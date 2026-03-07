// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.discord

import com.xpdustry.imperium.common.annotation.AnnotationScanner
import com.xpdustry.imperium.common.inject.MutableInstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.inject.provider
import com.xpdustry.imperium.common.network.Discovery
import com.xpdustry.imperium.common.version.ImperiumVersion
import com.xpdustry.imperium.discord.command.DiscordCommandDispatcher
import com.xpdustry.imperium.discord.content.MindustryContentHandler
import com.xpdustry.imperium.discord.content.MindustryToolContentHandler
import com.xpdustry.imperium.discord.service.DiscordService
import com.xpdustry.imperium.discord.service.SimpleDiscordService
import java.nio.file.Path
import java.util.function.Supplier
import kotlin.io.path.Path

fun MutableInstanceManager.registerDiscordModule() {
    provider<DiscordService> { SimpleDiscordService(get(), get(), get()) }

    provider<Path>("directory") { Path(".") }

    provider<AnnotationScanner> { DiscordCommandDispatcher(get(), get()) }

    provider<MindustryContentHandler> { MindustryToolContentHandler(get()) }

    provider<Supplier<Discovery.Data>>("discovery") { Supplier { Discovery.Data.Discord } }

    provider<ImperiumVersion> {
        ImperiumVersion.parse(this::class.java.getResourceAsStream("/imperium-version.txt")!!.reader().readText())
    }
}
