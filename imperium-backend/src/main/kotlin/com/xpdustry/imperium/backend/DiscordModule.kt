// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.backend

import com.xpdustry.imperium.backend.command.DiscordCommandDispatcher
import com.xpdustry.imperium.backend.content.AnukenMindustryContentHandler
import com.xpdustry.imperium.backend.content.MindustryContentHandler
import com.xpdustry.imperium.backend.service.DiscordService
import com.xpdustry.imperium.backend.service.SimpleDiscordService
import com.xpdustry.imperium.common.annotation.AnnotationScanner
import com.xpdustry.imperium.common.dependency.DependencyService
import com.xpdustry.imperium.common.network.Discovery
import com.xpdustry.imperium.common.network.DiscoveryDataSupplier
import com.xpdustry.imperium.common.version.ImperiumVersion
import java.nio.file.Path
import java.util.jar.Manifest
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
    bindToImpl<MindustryContentHandler, AnukenMindustryContentHandler>()
}

private fun createDiscordDirectory(): Path = Path(".")

private fun createDiscordDiscoveryDataSupplier(): DiscoveryDataSupplier = DiscoveryDataSupplier {
    Discovery.Data.Discord
}

private fun createDiscordImperiumVersion(): ImperiumVersion =
    ImperiumVersion.parse(
        ImperiumBackend::class.java.classLoader.getResourceAsStream("META-INF/MANIFEST.MF")!!.use { stream ->
            Manifest(stream).mainAttributes.getValue("Implementation-Version")
        }
    )
