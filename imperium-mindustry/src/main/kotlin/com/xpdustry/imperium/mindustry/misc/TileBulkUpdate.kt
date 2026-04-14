// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.misc

import mindustry.content.Blocks
import mindustry.game.Team
import mindustry.gen.Call
import mindustry.world.Block
import mindustry.world.Tile
import mindustry.world.blocks.environment.OverlayFloor

fun Iterable<Tile>.setBlocksNet(block: Block, team: Team = Team.derelict, rotation: Int = 0) {
    val tiles = toList()
    if (tiles.isEmpty()) return

    if (block == Blocks.air || !block.rotate || rotation == 0) {
        Call.setTileBlocks(block, team, tiles.packedPositions())
    } else {
        tiles.forEach { Call.setTile(it, block, team, rotation) }
    }
}

fun Iterable<Tile>.setFloorsNet(floor: Block) {
    val positions = packedPositions()
    if (positions.isNotEmpty()) {
        Call.setTileFloors(floor, positions)
    }
}

fun Iterable<Tile>.setOverlaysNet(overlay: Block) {
    val tiles = toList()
    if (tiles.isEmpty()) return

    if (overlay is OverlayFloor) {
        Call.setTileOverlays(overlay, tiles.packedPositions())
    } else {
        tiles.forEach { Call.setOverlay(it, overlay) }
    }
}

private fun Iterable<Tile>.packedPositions(): IntArray = map(Tile::pos).toIntArray()
