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
