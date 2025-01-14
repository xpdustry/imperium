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
package com.xpdustry.imperium.mindustry.game.formation

import arc.math.Mathf
import arc.math.geom.Vec2
import arc.util.Time
import mindustry.entities.units.AIController
import mindustry.gen.Call
import mindustry.gen.Unit

class FormationAI(private val leader: Unit, private val context: FormationContext) : AIController(), FormationMember {

    override var id: Int = -1
    override val targetVector = Vec2()

    override fun init() {
        id = unit.id
        targetVector.set(unit.x, unit.y)
    }

    override fun updateUnit() {
        if (leader.dead || context.deleted || !context.members.contains(this)) {
            context.remove(this)
            unit.resetController()
            return
        }

        if (unit.type.canBoost) {
            unit.elevation =
                Mathf.approachDelta(
                    unit.elevation,
                    if (unit.onSolid()) 1f
                    else // definitely cannot land
                    if (unit.isFlying && !unit.canLand()) unit.elevation
                    else // try to maintain altitude
                    if (leader.type.canBoost) leader.elevation
                    else // follow leader
                     0f,
                    unit.type.riseSpeed,
                )
        }

        unit.controlWeapons(true, leader.isShooting)

        unit.aim(leader.aimX(), leader.aimY())

        if (unit.type.faceTarget) {
            unit.lookAt(leader.aimX(), leader.aimY())
        } else if (unit.moving()) {
            unit.lookAt(unit.vel.angle())
        }

        val realTarget = vec.set(targetVector).add(leader.vel)

        val speed = unit.speed() * Time.delta
        unit.approach(
            Mathf.arrive(unit.x, unit.y, realTarget.x, realTarget.y, unit.vel, speed, 0f, speed, Float.MAX_VALUE)
                .scl(1f / Time.delta)
        )

        if (unit.canMine() && leader.canMine()) {
            if (leader.mineTile != null && unit.validMine(leader.mineTile)) {
                unit.mineTile(leader.mineTile)

                val core = unit.team.core()

                if (
                    core != null &&
                        leader.mineTile.drop() != null &&
                        unit.within(core, unit.type.range) &&
                        !unit.acceptsItem(leader.mineTile.drop())
                ) {
                    if (core.acceptStack(unit.stack.item, unit.stack.amount, unit) > 0) {
                        Call.transferItemTo(unit, unit.stack.item, unit.stack.amount, unit.x, unit.y, core)

                        unit.clearItem()
                    }
                }
            } else {
                unit.mineTile(null)
            }
        }

        if (unit.canBuild() && leader.canBuild() && leader.activelyBuilding()) {
            unit.clearBuilding()
            unit.addBuild(leader.buildPlan())
        }
    }

    override fun removed(unit: Unit) {
        context.remove(this)
        unit.resetController()
    }

    override fun isBeingControlled(player: Unit) = leader == player

    override fun isLogicControllable() = false

    override fun isValid() = unit().controller() == this
}
