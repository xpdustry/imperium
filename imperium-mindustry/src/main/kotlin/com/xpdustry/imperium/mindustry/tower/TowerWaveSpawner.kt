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
package com.xpdustry.imperium.mindustry.tower

import arc.util.Time
import arc.util.Tmp
import mindustry.Vars
import mindustry.ai.WaveSpawner

class TowerWaveSpawner : WaveSpawner() {

    private var spawning: Boolean
        get() = SPAWNING_FIELD.get(this) as Boolean
        set(value) = SPAWNING_FIELD.setBoolean(this, value)

    override fun spawnEnemies() {
        spawning = true

        eachSpawn(-1) { spawnX, spawnY -> doShockwave(spawnX, spawnY) }

        for (group in Vars.state.rules.spawns) {
            if (group.type == null) continue
            val spawned = group.getSpawned(Vars.state.wave - 1)
            val spread = (Vars.tilesize * 2).toFloat()
            eachSpawn(group.spawn) { spawnX, spawnY ->
                for (i in 0 until spawned) {
                    Tmp.v1.rnd(spread)
                    val unit = group.createUnit(Vars.state.rules.waveTeam, Vars.state.wave - 1)
                    unit[spawnX + Tmp.v1.x] = spawnY + Tmp.v1.y
                    spawnEffect(unit)
                }
            }
        }

        Time.run(121f) { spawning = false }
    }

    private fun eachSpawn(filterPos: Int, cons: SpawnConsumer) {
        if (Vars.state.hasSpawns()) {
            for (spawn in spawns) {
                if (filterPos != -1 && filterPos != spawn.pos()) continue
                cons.accept(spawn.worldx(), spawn.worldy())
            }
        }
    }

    private fun interface SpawnConsumer {
        fun accept(x: Float, y: Float)
    }

    companion object {
        private val SPAWNING_FIELD = WaveSpawner::class.java.getDeclaredField("spawning")

        init {
            SPAWNING_FIELD.isAccessible = true
        }
    }
}
