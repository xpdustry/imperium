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
package com.xpdustry.imperium.mindustry.event

import arc.Events
import com.xpdustry.distributor.api.annotation.EventHandler
import com.xpdustry.distributor.api.annotation.TaskHandler
import com.xpdustry.distributor.api.command.CommandSender
import com.xpdustry.distributor.api.scheduler.MindustryTimeUnit
import com.xpdustry.imperium.common.account.Rank
import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.command.ImperiumCommand
import com.xpdustry.imperium.common.content.MindustryGamemode
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.misc.LoggerDelegate
import com.xpdustry.imperium.mindustry.command.annotation.ClientSide
import com.xpdustry.imperium.mindustry.command.annotation.Flag
import com.xpdustry.imperium.mindustry.command.annotation.Scope
import com.xpdustry.imperium.mindustry.game.MenuToPlayEvent
import kotlin.random.Random
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import mindustry.Vars
import mindustry.content.Blocks
import mindustry.game.EventType.BlockBuildBeginEvent
import mindustry.game.EventType.GameOverEvent
import mindustry.game.Team
import mindustry.gen.Call
import mindustry.world.Tile
import mindustry.world.blocks.ConstructBlock
import mindustry.world.blocks.ConstructBlock.ConstructBuild

class EventListener(instances: InstanceManager) : ImperiumApplication.Listener {
    private val validTiles = mutableListOf<Pair<Int, Int>>()
    private val crates = mutableListOf<Triple<Int, Int, Int>>()
    private var delayJob: Job? = null

    @EventHandler
    fun onDelayStart(event: MenuToPlayEvent) {
        delayJob =
            ImperiumScope.MAIN.launch {
                delay(Random.nextLong(3 * 60, 13 * 60).seconds)
                while (isActive) {
                    onCrateGenerate()
                    delay(Random.nextLong(3 * 60, 13 * 60).seconds)
                }
            }
    }

    @EventHandler
    fun onDelayRemove(event: GameOverEvent) {
        delayJob?.cancel()
        delayJob = null
    }

    @ImperiumCommand(["crate"], Rank.ADMIN)
    @Scope(MindustryGamemode.EVENT)
    @ClientSide
    fun onManualGenerateCommand(
        sender: CommandSender,
        x: Int = 0,
        y: Int = 0,
        @Flag rarity: Int = 0
    ) {
        generateCrate(x, y, rarity)
    }

    fun onCrateGenerate() {
        val localValidTiles = validTiles.toMutableList()
        if (localValidTiles.isEmpty()) {
            return LOGGER.error("How is the entire map full??")
            Events.fire(GameOverEvent(Team.derelict))
            Call.sendMessage(
                "[scarlet]The map has ended due to no valid tiles left to spawn crates!")
        }

        while (localValidTiles.isNotEmpty()) {
            val randomTile = localValidTiles.random()
            val (x, y) = randomTile
            if (checkValid(x, y)) {
                generateCrate(x, y, 0)
                return
            } else {
                localValidTiles.remove(randomTile)
            }
        }
        LOGGER.error(
            "Failed to generate crate: No valid tiles left.") // tmp log, shout at players instead
        registerValidTiles()
    }

    @TaskHandler(interval = 10L, unit = MindustryTimeUnit.SECONDS)
    fun registerValidTiles() {
        for (x in 0..Vars.world.width()) {
            for (y in 0..Vars.world.height()) {
                if (checkValid(x, y)) {
                    validTiles.add(x to y)
                }
            }
        }
    }

    fun generateCrate(x: Int, y: Int, rarity: Int) {
        val newRarity = rarity.takeIf { it != 0 } ?: generateRarity()
        val tile = Vars.world.tile(x, y)

        tile.setNet(Blocks.vault)
        crates.add(Triple(x, y, newRarity))
        Call.label("Event Vault", Float.MAX_VALUE, (x * 8).toFloat(), (y * 8).toFloat())
    }

    @EventHandler
    fun onCrateDeletion(event: BlockBuildBeginEvent) {
        println("\nonCrateDeletion()\n") // testing
        if (!event.breaking) return
        val building = event.tile.build
        if (building is ConstructBlock.ConstructBuild) {
            val block = building.current
            if (block != Blocks.vault) return
        }
        val crate =
            crates.find { it.first == event.tile.x.toInt() && it.second == event.tile.y.toInt() }
        if (crate == null) return
        val rarity = crate.third
        handleCrateRemoval(rarity, event.tile, event.team)
    }

    fun generateRarity(): Int {
        println("\ngenerateRarity()\n") // testing
        val randomValue = Random.nextDouble(0.0, 100.0)
        return when {
            randomValue < 0.5 -> 6
            randomValue < 2 -> 5
            randomValue < 10 -> 4
            randomValue < 25 -> 3
            randomValue < 50 -> 2
            else -> 1
        }
    }

    fun handleCrateRemoval(rarity: Int, tile: Tile, team: Team) {
        println("\nhandleCrateRemoval()\n") // testing
        val crate = getVaultByRarity(rarity).random()
        crate.effect(tile.x.toInt(), tile.y.toInt(), team)
        crates.removeIf {
            it.first == tile.x.toInt() && it.second == tile.y.toInt() && it.third == rarity
        }
        tile.setNet(Blocks.air)
    }

    fun checkValid(x: Int, y: Int): Boolean {
        return (x - 1..x + 1).all { x1 ->
            (y - 1..y + 1).all { y1 ->
                val tile = Vars.world.tile(x1, y1)
                tile != null && tile.block() == Blocks.air
            }
        }
    }

    companion object {
        private val LOGGER by LoggerDelegate()
    }
}
