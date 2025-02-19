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
package com.xpdustry.imperium.mindustry.world

import arc.struct.Seq
import com.xpdustry.distributor.api.command.CommandSender
import com.xpdustry.distributor.api.command.cloud.specifier.AllTeams
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.command.annotation.Flag
import com.xpdustry.imperium.mindustry.misc.Rotation
import com.xpdustry.imperium.mindustry.misc.reloadWorldData
import mindustry.Vars
import mindustry.content.Blocks
import mindustry.game.Team
import mindustry.gen.Groups
import mindustry.gen.Player
import mindustry.world.Block
import mindustry.world.blocks.environment.Prop
import mindustry.world.blocks.environment.TreeBlock
import org.incendo.cloud.annotation.specifier.Range

// TODO: Translate these
class WorldEditCommand : ImperiumApplication.Listener {

    @ImperiumCommand(["wedit"], Rank.MODERATOR)
    @ClientSide
    fun onWorldEditCommand(
        sender: CommandSender,
        @Range(min = "0") @Flag("x") x: Int = sender.player.tileX(),
        @Range(min = "0") @Flag("y") y: Int = sender.player.tileY(),
        @Range(min = "1", max = "100") @Flag("w") w: Int = 1,
        @Range(min = "1", max = "100") @Flag("h") h: Int = 1,
        @Flag("b") block: Block? = null,
        @Flag("t") @AllTeams team: Team = sender.player.team(),
        @Flag("r") rotation: Rotation = Rotation.UP,
        @Flag("f") floor: Block? = null,
        @Flag("o") overlay: Block? = null,
        @Flag override: Boolean,
    ) {
        val size = block?.size ?: 1
        val x2 = x + w + (size % (x + w))
        val y2 = y + h + (size % (y + h))
        if (
            x > (Vars.world.width() + 1) ||
                y > (Vars.world.height() + 1) ||
                x2 > (Vars.world.width() + 1) ||
                y2 > (Vars.world.height() + 1)
        ) {
            sender.error("The specified coordinates and size are out of bounds.")
            return
        }
        if (floor != null && !floor.isFloor) {
            sender.error("The block $floor is not a floor.")
            return
        }
        if (overlay != null && !overlay.isOverlay) {
            sender.error("The block $overlay is not an overlay.")
            return
        }
        if (
            block != null &&
                !(block.isStatic || block.hasBuilding() || block.isAir || block is Prop || block is TreeBlock)
        ) {
            sender.error("The block $block is not a static, placeable, prop or air block.")
            return
        }
        if (block == null && floor == null && overlay == null) {
            sender.error("You must specify at least one of block, floor, or overlay.")
            return
        }

        repeat(w) { ox ->
            repeat(h) { oy ->
                if (ox % size == 0 && oy % size == 0) {
                    val tile = Vars.world.tile(x + ox, y + oy)
                    floor?.let { tile.setFloorNet(it.asFloor()) }
                    overlay?.let { tile.setOverlayNet(it.asFloor()) }
                    if (block != null && (override || tile.getLinkedTilesAs(block, Seq()).all { it.build == null })) {
                        tile.setNet(Blocks.air)
                        tile.setNet(block, team, rotation.ordinal)
                    }
                }
            }
        }

        // Reindex the spawn blocks
        if (overlay == Blocks.spawn) {
            Vars.spawner.reset()
            Groups.player.each(Player::reloadWorldData)
        }

        sender.reply(
            buildString {
                if (w != 1 || h != 1) {
                    append("Set tiles between ($x, $y) and (${x + w - 1}, ${y + h - 1}) to ")
                } else {
                    append("Set tile at ($x, $y) to ")
                }
                if (block != null) append("block $block, ")
                if (floor != null) append("floor $floor, ")
                if (overlay != null) append("overlay $overlay, ")
                append("for team ${team.coloredName()}.")
            }
        )
    }
}
