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

import com.xpdustry.distributor.api.annotation.EventHandler
import java.util.Timer
import kotlin.concurrent.schedule
import mindustry.game.EventType
import mindustry.type.unit.MissileUnitType

class PointManager(var points: Int = 200) {
    private val unitPoints =
        mapOf(
            "dagger" to 1,
            "nova" to 1,
            "crawler" to 1,
            "mace" to 5,
            "pulsar" to 5,
            "atrax" to 5,
            "fortress" to 10,
            "quasar" to 10,
            "spiroct" to 10,
            "scepter" to 15,
            "vela" to 15,
            "arkyid" to 15,
            "reign" to 25,
            "corvus" to 25,
            "toxopid" to 25,
            "risso" to 1,
            "retusa" to 1,
            "minke" to 5,
            "oxynoe" to 5,
            "bryde" to 10,
            "cyerce" to 10,
            "sei" to 15,
            "aegires" to 15,
            "omura" to 15,
            "navanax" to 25,
            "flare" to 1,
            // no mono as it cannot attack
            "horizon" to 5,
            "poly" to 5,
            "zenith" to 10,
            "mega" to 10,
            "antumbra" to 15,
            "quad" to 15,
            "eclipse" to 25,
            // no oct as it cannot attack

            // erekir units
            "stell" to 1,
            "elude" to 1,
            "merui" to 1,
            "locus" to 5,
            "avert" to 5,
            "cleroi" to 5,
            "precept" to 10,
            "anthicus" to 10,
            "obviate" to 10,
            "vanquish" to 15,
            "quell" to 15,
            "tecta" to 15,
            "conquer" to 25,
            "disrupt" to 25,
            "collaris" to 25,
        )

    private fun getUnitTier(unitType: String): Int? {
        return unitPoints[unitType] ?: null
    }

    private fun onPassiveGain(points: Int) {
        return points += 20
    }

    @EventHandler
    private fun onUnitDestroyEvent(event: EventType.UnitDestroyEvent) {
        if (event.unit().team() != Vars.state.rules.waveTeam && event.unit() !is MissileUnitType) {
            val increment = getUnitTier(unit.type.name)
            if (point != null) {
                points += increment
            }
        }
    }

    fun pointManager() {
        Timer().schedule(0, 1000) { onPassiveGain() }
    }

    fun getPoints() {
        return points
    }
}
