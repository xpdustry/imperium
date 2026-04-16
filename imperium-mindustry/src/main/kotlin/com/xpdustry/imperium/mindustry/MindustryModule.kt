// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry

import arc.Core
import com.xpdustry.distributor.api.component.render.ComponentRendererProvider
import com.xpdustry.distributor.api.plugin.MindustryPlugin
import com.xpdustry.flex.translator.Translator
import com.xpdustry.imperium.common.bridge.PlayerTracker
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.config.MindustryConfig
import com.xpdustry.imperium.common.dependency.DependencyService
import com.xpdustry.imperium.common.network.DiscoveryDataSupplier
import com.xpdustry.imperium.common.version.ImperiumVersion
import com.xpdustry.imperium.mindustry.bridge.MindustryPlayerTracker
import com.xpdustry.imperium.mindustry.chat.MindustryAudienceFormatter
import com.xpdustry.imperium.mindustry.chat.MindustryMessagePipeline
import com.xpdustry.imperium.mindustry.chat.SimpleMindustryMessagePipeline
import com.xpdustry.imperium.mindustry.command.CommandAnnotationScanner
import com.xpdustry.imperium.mindustry.component.ImperiumComponentRendererProvider
import com.xpdustry.imperium.mindustry.game.ClientDetector
import com.xpdustry.imperium.mindustry.game.SimpleClientDetector
import com.xpdustry.imperium.mindustry.history.Historian
import com.xpdustry.imperium.mindustry.history.HistoryRenderer
import com.xpdustry.imperium.mindustry.history.SimpleHistorian
import com.xpdustry.imperium.mindustry.history.SimpleHistoryRenderer
import com.xpdustry.imperium.mindustry.misc.getMindustryServerInfo
import com.xpdustry.imperium.mindustry.security.AfkListener
import com.xpdustry.imperium.mindustry.security.AfkManager
import com.xpdustry.imperium.mindustry.security.BadWordDetector
import com.xpdustry.imperium.mindustry.security.GatekeeperPipeline
import com.xpdustry.imperium.mindustry.security.MarkedPlayerManager
import com.xpdustry.imperium.mindustry.security.SimpleBadWordDetector
import com.xpdustry.imperium.mindustry.security.SimpleGatekeeperPipeline
import com.xpdustry.imperium.mindustry.security.SimpleMarkedPlayerManager
import com.xpdustry.imperium.mindustry.store.DataStoreService
import java.nio.file.Path
import java.util.concurrent.Executor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor

internal fun DependencyService.Binder.registerMindustryModule(plugin: MindustryPlugin) {
    // Plugin runtime.
    bindToProv<MindustryPlugin> { plugin }
    bindToFunc<Path>(::getPluginDirectory, "directory")
    bindToFunc<Executor>(::createMindustryMainExecutor, "main")

    // Server identity.
    bindToFunc<DiscoveryDataSupplier>(::createMindustryDiscoveryDataSupplier)
    bindToFunc<ImperiumVersion>(::createMindustryImperiumVersion)
    bindToImpl<PlayerTracker, MindustryPlayerTracker>()

    // Gameplay services.
    bindToImpl<Historian, SimpleHistorian>()
    bindToImpl<DataStoreService, DataStoreService>()
    bindToImpl<ClientDetector, SimpleClientDetector>()
    bindToImpl<GatekeeperPipeline, SimpleGatekeeperPipeline>()
    bindToImpl<BadWordDetector, SimpleBadWordDetector>()
    bindToImpl<MarkedPlayerManager, SimpleMarkedPlayerManager>()
    bindToImpl<AfkManager, AfkListener>()

    // Chat and history.
    bindToFunc<Translator>(::createTranslator)
    bindToImpl<HistoryRenderer, SimpleHistoryRenderer>()
    bindToImpl<MindustryAudienceFormatter, MindustryAudienceFormatter>()
    bindToImpl<MindustryMessagePipeline, SimpleMindustryMessagePipeline>()

    // UI integration.
    bindToImpl<ComponentRendererProvider, ImperiumComponentRendererProvider>()
    bindToImpl<CommandAnnotationScanner, CommandAnnotationScanner>()
}

private fun getPluginDirectory(plugin: MindustryPlugin): Path = plugin.directory

private fun createMindustryDiscoveryDataSupplier(): DiscoveryDataSupplier =
    DiscoveryDataSupplier(::getMindustryServerInfo)

private fun createMindustryImperiumVersion(plugin: MindustryPlugin): ImperiumVersion =
    ImperiumVersion.parse(plugin.metadata.version)

private fun createMindustryMainExecutor(): Executor = Executor(Core.app::post)

private fun createTranslator(config: ImperiumConfig): Translator {
    val executor = Dispatchers.IO.asExecutor()

    fun create(backend: MindustryConfig.Chat.Translation.Backend): Translator =
        when (backend) {
            is MindustryConfig.Chat.Translation.Backend.None -> Translator.noop()
            is MindustryConfig.Chat.Translation.Backend.LibreTranslate ->
                Translator.libreTranslate(backend.ltEndpoint, executor, backend.ltApiKey?.value)
            is MindustryConfig.Chat.Translation.Backend.DeepL -> Translator.deepl(backend.deeplApiKey.value, executor)
            is MindustryConfig.Chat.Translation.Backend.GoogleBasic ->
                Translator.googleBasic(backend.googleBasicApiKey.value, executor)
            is MindustryConfig.Chat.Translation.Backend.Rolling ->
                Translator.rolling(backend.translators.map(::create), create(backend.fallback))
            is MindustryConfig.Chat.Translation.Backend.Caching ->
                Translator.caching(
                    create(backend.cachingTranslator),
                    executor,
                    backend.maximumSize,
                    backend.successRetention.inWholeMilliseconds.let(java.time.Duration::ofMillis),
                    backend.failureRetention.inWholeMilliseconds.let(java.time.Duration::ofMillis),
                )
        }

    return create(config.mindustry.chat.translation.backend)
}
