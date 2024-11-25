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
}
