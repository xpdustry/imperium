// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.game

import com.xpdustry.distributor.api.Distributor
import com.xpdustry.imperium.common.application.ImperiumApplication
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.util.zip.InflaterInputStream
import mindustry.Vars
import mindustry.logic.LAssembler
import mindustry.logic.LExecutor.UnitControlI
import mindustry.logic.LUnitControl
import mindustry.net.Administration
import mindustry.world.blocks.logic.LogicBlock

class LogicListener : ImperiumApplication.Listener {

    override fun onImperiumInit() {
        Vars.netServer.admins.addActionFilter { action ->
            val block =
                when (action.type) {
                    Administration.ActionType.placeBlock -> action.block
                    Administration.ActionType.configure -> action.tile.block()
                    else -> null
                }
                    as? LogicBlock ?: return@addActionFilter true

            val config = action.config
            if (config !is ByteArray) {
                return@addActionFilter true
            }

            val assembler = assemble(block, config) ?: return@addActionFilter true
            if (assembler.instructions.any { it is UnitControlI && it.type == LUnitControl.build }) {
                val permissions = Distributor.get().playerPermissionProvider.getPermissions(action.player)
                return@addActionFilter permissions.getPermission("imperium.rank.overseer").asBoolean() ||
                    permissions.getPermission("imperium.achievement.active").asBoolean()
            }

            return@addActionFilter true
        }
    }

    private fun assemble(block: LogicBlock, bytes: ByteArray) =
        try {
            DataInputStream(InflaterInputStream(ByteArrayInputStream(bytes))).use { stream ->
                stream.read() // Skip version
                val length = stream.readInt()
                if (length > MAX_INSTRUCTION_LENGTH) return@use null
                val raw = ByteArray(length)
                stream.readFully(raw)
                LAssembler.assemble(String(raw, Vars.charset), block.privileged)
            }
        } catch (ignored: Exception) {
            null
        }

    companion object {
        private const val MAX_INSTRUCTION_LENGTH = 1024 * 100
    }
}
