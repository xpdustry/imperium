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

import arc.math.Mathf
import arc.util.Tmp
import com.xpdustry.imperium.common.misc.LoggerDelegate
import com.xpdustry.imperium.mindustry.misc.ImmutablePoint
import mindustry.Vars
import mindustry.ai.types.FlyingAI
import mindustry.ai.types.GroundAI
import mindustry.entities.Units
import mindustry.entities.units.AIController
import mindustry.gen.Teamc
import mindustry.world.blocks.storage.CoreBlock

internal class TowerAI(private val manager: WaypointManager) : AIController() {

    private var state = State.IDLE
    private var lastPathId = 0
    private var moveX = 0f
    private var moveY = 0f
    private var lastMoveX = 0f
    private var lastMoveY = 0f
    private var waypoint = UNDEFINED_WAYPOINT

    override fun init() {
        fallback = if (!unit.type.flying) GroundAI() else FlyingAI()
    }

    override fun updateMovement() {
        if (state == State.GOTO_CORE) {
            return
        }

        if (state == State.IDLE && (unit.x != 0f || unit.y != 0f)) {
            waypoint =
                manager.waypoints.minByOrNull {
                    Mathf.dst(
                        it.x * Vars.tilesize.toFloat(),
                        it.y * Vars.tilesize.toFloat(),
                        unit.x,
                        unit.y)
                } ?: UNDEFINED_WAYPOINT
            state =
                if (waypoint != UNDEFINED_WAYPOINT) {
                    State.FOLLOW_PATH
                } else {
                    State.GOTO_CORE
                }
        }

        if (state == State.IDLE) {
            return
        }

        if (Mathf.within(
            unit.x,
            unit.y,
            waypoint.x * Vars.tilesize.toFloat(),
            waypoint.y * Vars.tilesize.toFloat(),
            3F * Vars.tilesize)) {
            val links = manager.getLinks(waypoint)
            if (links.isEmpty()) {
                state = State.GOTO_CORE
                return
            }
            waypoint = links.random()
        }

        moveX = waypoint.x * Vars.tilesize.toFloat()
        moveY = waypoint.y * Vars.tilesize.toFloat()

        if (!Mathf.equal(moveX, lastMoveX, 0.1f) || !Mathf.equal(moveY, lastMoveY, 0.1f)) {
            lastPathId++
            lastMoveX = moveX
            lastMoveY = moveY
        }

        if (unit.isFlying) {
            moveTo(Tmp.v1.set(moveX, moveY), 1f, 30f)
        } else {
            if (Vars.controlPath.getPathPosition(
                unit, lastPathId, Tmp.v2.set(moveX, moveY), Tmp.v1, null)) {
                moveTo(Tmp.v1, 1f, if (Tmp.v2.epsilonEquals(Tmp.v1, 4.1f)) 30f else 0f)
            }
        }

        if (unit.type.canBoost && !unit.type.flying) {
            unit.elevation =
                Mathf.approachDelta(
                    unit.elevation,
                    Mathf.num(unit.onSolid() || (unit.isFlying && !unit.canLand())).toFloat(),
                    unit.type.riseSpeed)
        }

        // look where moving if there's nothing to aim at
        if (!unit.isShooting || !unit.type.omniMovement) {
            unit.lookAt(unit.prefRotation())
        } else if (unit.hasWeapons() &&
            unit.mounts.isNotEmpty() &&
            !unit.mounts[0].weapon.ignoreRotation) { // if there is, look at the object
            unit.lookAt(unit.mounts[0].aimX, unit.mounts[0].aimY)
        }
    }

    override fun target(x: Float, y: Float, range: Float, air: Boolean, ground: Boolean): Teamc? {
        return Units.closestTarget(
            unit.team, x, y, range, { false }, { ground && it.block() is CoreBlock })
    }

    override fun useFallback(): Boolean {
        return state == State.GOTO_CORE
    }

    override fun retarget(): Boolean {
        return true
    }

    companion object {
        private val UNDEFINED_WAYPOINT = ImmutablePoint(-1, -1)
        private val LOGGER by LoggerDelegate()
    }

    private enum class State {
        GOTO_CORE,
        FOLLOW_PATH,
        IDLE
    }
}
