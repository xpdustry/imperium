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

import arc.graphics.Color
import arc.math.geom.Vec2
import com.xpdustry.imperium.mindustry.misc.toWorldFloat
import mindustry.content.UnitTypes
import mindustry.game.Team
import mindustry.gen.*
import mindustry.graphics.Fx
import mindustry.graphics.Pal
import mindustry.type.*
import mindustry.type.ammo.*
import mindustry.type.unit.*
import mindustry.type.weapons.*

data class Vault(
    val name: String,
    val rarity: Int,
    val positive: Boolean,
    val effect: (Int, Int, Team) -> Unit
)

fun getVaultByRarity(rarity: Int): List<Vault> {
    return when (rarity) {
        1 -> VaultTypes.commonVault
        2 -> VaultTypes.uncommonVault
        3 -> VaultTypes.rareVault
        4 -> VaultTypes.epicVault
        5 -> VaultTypes.legendaryVault
        6 -> VaultTypes.mythicVault
        else -> emptyList()
    }
}

object VaultTypes {
    val commonVault =
        listOf(
            Vault("Electric Dagger", 1, true) { x1, y, team ->
                repeat(1) {
                    val unit =
                        UnitTypes.dagger.spawn(Vec2(x1.toWorldFloat(), y.toWorldFloat()), team)
                    unit.weapons.clear() // remove old weapon
                    unit.weapons.add(
                        Weapon("large-weapon").apply {
                            top = false
                            shake = 2f
                            shootY = 4f // probably need to change this
                            x = 4f
                            y = 2f
                            reload = 55f // dagger 13f
                            shootSound = Sounds.laser

                            bullet =
                                LaserBulletType().apply {
                                    damage = 45f
                                    recoil = 1f
                                    sideAngle = 45f
                                    sideLength = 70f
                                    healPercent = 10f
                                    collidesTeam = true
                                    length = 135f
                                    colors = arrayOf(Pal.heal.cpy().a(0.4f), Pal.heal, Color.white)
                                }
                        })
                }
            },
            Vault("test2", 1, false) { x, y, team -> println("i dont want to finish this") },
        )

    val uncommonVault =
        listOf(
            Vault("Crawler Bomb", 2, true) { x1, y, team ->
                val unit = UnitTypes.crawler.spawn(Vec2(x1.toWorldFloat(), y.toWorldFloat()), team)
                unit.weapons.clear()
                unit.weapons.add(
                    Weapon().apply {
                        shootOnDeath = true
                        targetUnderBlocks = false
                        reload = 24f
                        shootCone = 180f
                        ejectEffect = Fx.none
                        shootSound = Sounds.plasmadrop
                        x = shootY = 0f
                        mirror = false

                        bullet =
                            BulletType().apply {
                                sprite = "large-bomb"
                                width = height = 120 / 4f
                                maxRange = 30f
                                ignoreRotation = true
                                hitSound = Sounds.plasmadrop
                                shootCone = 180f
                                ejectEffect = Fx.none
                                collidesAir = false
                                lifetime = 70f
                                despawnEffect = Fx.greenBomb
                                hitEffect = Fx.massiveExplosion
                                keepVelocity = false
                                spin = 2f
                                shrinkX = shrinkY = 0.7f
                                speed = 0f
                                collides = false
                                healPercent = 15f
                                splashDamage = 220f
                                splashDamageRadius = 80f
                                damage = splashDamage * 0.7f
                            }
                    })
            },
            Vault("test2", 2, false) { x, y, team ->
                // Todo
            },
        )

    val rareVault =
        listOf(
            Vault("test1", 3, true) { x, y, team ->
                // Todo
            },
            Vault("test2", 3, false) { x, y, team ->
                // Todo
            },
        )

    val epicVault =
        listOf(
            Vault("test1", 4, true) { x, y, team ->
                // Todo
            },
            Vault("test2", 4, false) { x, y, team ->
                // Todo
            },
        )

    val legendaryVault =
        listOf(
            Vault("test1", 5, true) { x, y, team ->
                // Todo
            },
            Vault("test2", 5, false) { x, y, team ->
                // Todo
            },
        )

    val mythicVault =
        listOf(
            Vault("test1", 6, true) { x, y, team ->
                // Todo
            },
            Vault("test2", 6, true) { x, y, team ->
                // Todo
            },
        )
}
