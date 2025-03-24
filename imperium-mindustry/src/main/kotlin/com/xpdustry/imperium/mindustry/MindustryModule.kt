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

import com.xpdustry.distributor.api.plugin.MindustryPlugin
import com.xpdustry.imperium.common.bridge.PlayerTracker
import com.xpdustry.imperium.common.factory.ObjectBinder
import com.xpdustry.imperium.common.factory.ObjectModule
import com.xpdustry.imperium.common.lifecycle.PlatformExitService
import com.xpdustry.imperium.common.network.DiscoveryDataSupplier
import com.xpdustry.imperium.common.version.ImperiumVersion
import com.xpdustry.imperium.mindustry.bridge.MindustryPlayerTracker
import com.xpdustry.imperium.mindustry.game.ClientDetector
import com.xpdustry.imperium.mindustry.game.SimpleClientDetector
import com.xpdustry.imperium.mindustry.history.Historian
import com.xpdustry.imperium.mindustry.history.HistoryRenderer
import com.xpdustry.imperium.mindustry.history.SimpleHistorian
import com.xpdustry.imperium.mindustry.history.SimpleHistoryRenderer
import com.xpdustry.imperium.mindustry.lifecycle.ExecutorWithLifecycle
import com.xpdustry.imperium.mindustry.lifecycle.MindustryExitService
import com.xpdustry.imperium.mindustry.misc.getMindustryServerInfo
import com.xpdustry.imperium.mindustry.security.BadWordDetector
import com.xpdustry.imperium.mindustry.security.GatekeeperPipeline
import com.xpdustry.imperium.mindustry.security.MarkedPlayerManager
import com.xpdustry.imperium.mindustry.security.SimpleBadWordDetector
import com.xpdustry.imperium.mindustry.security.SimpleGatekeeperPipeline
import com.xpdustry.imperium.mindustry.security.SimpleMarkedPlayerManager
import java.nio.file.Path
import java.util.concurrent.Executor

class MindustryModule(private val plugin: MindustryPlugin) : ObjectModule {
    override fun configure(binder: ObjectBinder) {
        binder.bind(PlatformExitService::class.java).toImpl(MindustryExitService::class.java)
        binder.bind(MindustryPlugin::class.java).toInst(plugin)
        binder.bind(ImperiumVersion::class.java).toInst(ImperiumVersion.parse(plugin.metadata.version))
        binder.bind(Historian::class.java).toImpl(SimpleHistorian::class.java)
        binder.bind(GatekeeperPipeline::class.java).toImpl(SimpleGatekeeperPipeline::class.java)
        binder.bind(Path::class.java).named("directory").toInst(plugin.directory)
        binder.bind(DiscoveryDataSupplier::class.java).toInst(DiscoveryDataSupplier(::getMindustryServerInfo))
        binder.bind(PlayerTracker::class.java).toImpl(MindustryPlayerTracker::class.java)
        binder.bind(ClientDetector::class.java).toImpl(SimpleClientDetector::class.java)
        binder.bind(BadWordDetector::class.java).toImpl(SimpleBadWordDetector::class.java)
        binder.bind(HistoryRenderer::class.java).toImpl(SimpleHistoryRenderer::class.java)
        binder.bind(MarkedPlayerManager::class.java).toImpl(SimpleMarkedPlayerManager::class.java)
        binder.bind(Executor::class.java).named("work").toImpl(ExecutorWithLifecycle::class.java)
    }
}
