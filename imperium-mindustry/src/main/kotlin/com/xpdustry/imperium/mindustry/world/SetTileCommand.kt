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

import com.xpdustry.distributor.command.CommandSender
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.command.annotation.Flag
import com.xpdustry.imperium.mindustry.misc.Rotation
import mindustry.Vars
import mindustry.game.Team
import mindustry.world.Block
import org.incendo.cloud.annotation.specifier.Range
import org.incendo.cloud.type.Either

// TODO Add width and height flags for large transformations
class SetTileCommand : ImperiumApplication.Listener {

    @ImperiumCommand(["set-tile"], Rank.ADMIN)
    @ClientSide
    fun onSetTileCommand(
        sender: CommandSender,
        @Range(min = "0") x: Int,
        @Range(min = "0") y: Int,
        @Flag("b") block: Block? = null,
        @Flag("r") rotation: Either<Rotation, Int> = Either.ofPrimary(Rotation.UP),
        @Flag("f") floor: Block? = null,
        @Flag("o") overlay: Block? = null,
        @Flag("t") team: Team = sender.player.team(),
    ) {
        val tile = Vars.world.tile(x, y)
        if (tile == null) {
            sender.sendWarning("The tile at ($x, $y) does not exist.")
            return
        }
        if (floor != null) {
            if (!floor.isFloor) {
                sender.sendWarning("The block $floor is not a floor.")
                return
            }
        }
        if (overlay != null) {
            if (!overlay.isOverlay) {
                sender.sendWarning("The block $overlay is not an overlay.")
                return
            }
        }
        if (block != null) {
            if (!(block.isStatic || block.isPlaceable)) {
                sender.sendWarning("The block $block is not a static or placeable block.")
                return
            }
        }

        if (block == null && floor == null && overlay == null) {
            sender.sendWarning("You must specify at least one of block, floor, or overlay.")
            return
        }

        floor?.let { tile.setFloorNet(it.asFloor()) }
        overlay?.let { tile.setOverlayNet(it.asFloor()) }
        block?.let { tile.setNet(it, team, rotation.fallbackOrMapPrimary(Rotation::ordinal)) }

        sender.sendMessage(
            buildString {
                append("Set tile at ($x, $y) to ")
                if (block != null) append("block $block, ")
                if (floor != null) append("floor $floor, ")
                if (overlay != null) append("overlay $overlay, ")
                append("for team ${team.coloredName()}.")
            })
    }
}
