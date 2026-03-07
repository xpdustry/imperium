// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry

import arc.Core
import com.xpdustry.distributor.api.plugin.MindustryPlugin
import com.xpdustry.imperium.common.bridge.PlayerTracker
import com.xpdustry.imperium.common.inject.MutableInstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.inject.provider
import com.xpdustry.imperium.common.network.Discovery
import com.xpdustry.imperium.common.version.ImperiumVersion
import com.xpdustry.imperium.mindustry.bridge.MindustryPlayerTracker
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

    provider<BadWordDetector> { SimpleBadWordDetector(get()) }

    provider<HistoryRenderer> { SimpleHistoryRenderer(get(), get(), get()) }

    provider<MarkedPlayerManager> { SimpleMarkedPlayerManager(plugin) }

    provider<AfkManager> { AfkListener(get(), get()) }
}
