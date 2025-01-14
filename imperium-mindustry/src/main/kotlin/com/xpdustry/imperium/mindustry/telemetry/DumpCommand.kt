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
package com.xpdustry.imperium.mindustry.telemetry

import com.sun.management.HotSpotDiagnosticMXBean
import com.xpdustry.distributor.api.command.CommandSender
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.mindustry.command.annotation.ServerSide
import java.lang.management.ManagementFactory
import java.nio.file.Path

class DumpCommand(instances: InstanceManager) : ImperiumApplication.Listener {

    private val directory = instances.get<Path>("directory").resolve("dumps")

    override fun onImperiumInit() {
        directory.toFile().mkdirs()
    }

    @ImperiumCommand(["dump"])
    @ServerSide
    fun onMemoryDumpCommand(sender: CommandSender, live: Boolean = true) {
        try {
            val server = ManagementFactory.getPlatformMBeanServer()
            val mxBean =
                ManagementFactory.newPlatformMXBeanProxy(
                    server,
                    "com.sun.management:type=HotSpotDiagnostic",
                    HotSpotDiagnosticMXBean::class.java,
                )
            val file = directory.resolve("${System.currentTimeMillis()}.hprof")
            mxBean.dumpHeap(file.toString(), live)
            sender.reply("Dumped heap to $file")
        } catch (e: Exception) {
            sender.reply("Failed to dump heap: ${e.message}")
        }
    }
}
