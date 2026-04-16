// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.discord

import com.xpdustry.imperium.common.annotation.AnnotationScanner
import com.xpdustry.imperium.common.dependency.DependencyService
import com.xpdustry.imperium.common.network.Discovery
import com.xpdustry.imperium.common.network.DiscoveryDataSupplier
import com.xpdustry.imperium.common.version.ImperiumVersion
import com.xpdustry.imperium.discord.command.DiscordCommandDispatcher
import com.xpdustry.imperium.discord.content.MindustryContentHandler
import com.xpdustry.imperium.discord.content.MindustryToolContentHandler
import com.xpdustry.imperium.discord.service.DiscordService
import com.xpdustry.imperium.discord.service.SimpleDiscordService
import java.nio.file.Path
import kotlin.io.path.Path

fun DependencyService.Binder.registerDiscordModule() {
    // Runtime.
    bindToImpl<DiscordService, SimpleDiscordService>()
    bindToFunc<Path>(::createDiscordDirectory, "directory")

    // Server identity.
    bindToFunc<DiscoveryDataSupplier>(::createDiscordDiscoveryDataSupplier)
    bindToFunc<ImperiumVersion>(::createDiscordImperiumVersion)

    // Discord tooling.
    bindToImpl<AnnotationScanner, DiscordCommandDispatcher>()
    bindToImpl<MindustryContentHandler, MindustryToolContentHandler>()
}

private fun createDiscordDirectory(): Path = Path(".")

private fun createDiscordDiscoveryDataSupplier(): DiscoveryDataSupplier = DiscoveryDataSupplier {
    Discovery.Data.Discord
}

private fun createDiscordImperiumVersion(): ImperiumVersion =
    ImperiumVersion.parse(
        SimpleDiscordService::class.java.getResourceAsStream("/imperium-version.txt")!!.reader().readText()
    )
