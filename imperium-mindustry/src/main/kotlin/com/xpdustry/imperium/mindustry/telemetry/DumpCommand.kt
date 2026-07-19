// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.telemetry

import com.sun.management.HotSpotDiagnosticMXBean
import com.xpdustry.distributor.api.command.CommandSender
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.dependency.Inject
import com.xpdustry.imperium.common.dependency.Named
import com.xpdustry.imperium.mindustry.command.annotation.ServerSide
import java.lang.management.ManagementFactory
import java.nio.file.Path

@Inject
class DumpCommand constructor(@Named("directory") directory: Path) : ImperiumApplication.Listener {

    private val directory = directory.resolve("dumps")

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
