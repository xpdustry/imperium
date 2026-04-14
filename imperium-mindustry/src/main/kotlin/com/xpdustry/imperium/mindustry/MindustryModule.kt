// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry

import arc.Core
import com.xpdustry.distributor.api.plugin.MindustryPlugin
import com.xpdustry.flex.translator.Translator
import com.xpdustry.imperium.common.bridge.PlayerTracker
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.config.MindustryConfig
import com.xpdustry.imperium.common.inject.MutableInstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.inject.provider
import com.xpdustry.imperium.common.network.Discovery
import com.xpdustry.imperium.common.version.ImperiumVersion
import com.xpdustry.imperium.mindustry.bridge.MindustryPlayerTracker
import com.xpdustry.imperium.mindustry.chat.MindustryAudienceFormatter
import com.xpdustry.imperium.mindustry.chat.MindustryMessagePipeline
import com.xpdustry.imperium.mindustry.chat.SimpleMindustryMessagePipeline
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
import java.nio.file.Path
import java.util.concurrent.Executor
import java.util.function.Supplier
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor

internal fun MutableInstanceManager.registerMindustryModule(plugin: MindustryPlugin) {
    provider<Historian> { SimpleHistorian(get(), get(), get(), get(), get()) }

    provider<MindustryPlugin> { plugin }

    provider<GatekeeperPipeline> { SimpleGatekeeperPipeline() }

    provider<Path>("directory") { plugin.directory }

    provider<Supplier<Discovery.Data>>("discovery") { Supplier(::getMindustryServerInfo) }

    provider<ImperiumVersion> { ImperiumVersion.parse(get<MindustryPlugin>().metadata.version) }

    provider<PlayerTracker> { MindustryPlayerTracker(get(), get(), get()) }

    provider<Executor>("main") { Executor(Core.app::post) }

    provider<ClientDetector> { SimpleClientDetector(get()) }

    provider<Translator> { createTranslator(get<ImperiumConfig>().mindustry.chat.translation.backend) }

    provider<MindustryAudienceFormatter> { MindustryAudienceFormatter(get(), get(), get(), get()) }

    provider<MindustryMessagePipeline> { SimpleMindustryMessagePipeline(get(), get()) }

    provider<BadWordDetector> { SimpleBadWordDetector(get()) }

    provider<HistoryRenderer> { SimpleHistoryRenderer(get(), get(), get()) }

    provider<MarkedPlayerManager> { SimpleMarkedPlayerManager(plugin) }

    provider<AfkManager> { AfkListener(get(), get()) }
}

private fun createTranslator(config: MindustryConfig.Chat.Translation.Backend): Translator {
    val executor = Dispatchers.IO.asExecutor()
    return when (config) {
        is MindustryConfig.Chat.Translation.Backend.None -> Translator.noop()
        is MindustryConfig.Chat.Translation.Backend.LibreTranslate ->
            Translator.libreTranslate(config.ltEndpoint, executor, config.ltApiKey?.value)
        is MindustryConfig.Chat.Translation.Backend.DeepL -> Translator.deepl(config.deeplApiKey.value, executor)
        is MindustryConfig.Chat.Translation.Backend.GoogleBasic ->
            Translator.googleBasic(config.googleBasicApiKey.value, executor)
        is MindustryConfig.Chat.Translation.Backend.Rolling ->
            Translator.rolling(config.translators.map(::createTranslator), createTranslator(config.fallback))
        is MindustryConfig.Chat.Translation.Backend.Caching ->
            Translator.caching(
                createTranslator(config.cachingTranslator),
                executor,
                config.maximumSize,
                config.successRetention.inWholeMilliseconds.let(java.time.Duration::ofMillis),
                config.failureRetention.inWholeMilliseconds.let(java.time.Duration::ofMillis),
            )
    }
}
