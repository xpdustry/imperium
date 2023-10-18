/*
 * Imperium, the software collection powering the Xpdustry network.
 * Copyright (C) 2023  Xpdustry
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
package com.xpdustry.imperium.mindustry.misc

import arc.struct.ObjectMap
import arc.struct.ObjectSet
import arc.struct.Seq
import fr.xpdustry.distributor.api.util.ArcCollections
import mindustry.entities.EntityGroup
import mindustry.gen.Entityc

fun <T> Seq<T>.toList(): List<T> = ArcCollections.immutableList(this)

fun <T : Entityc> EntityGroup<T>.toList(): List<T> = ArcCollections.immutableList(this)

fun <K, V> ObjectMap<K, V>.toMap(): Map<K, V> = ArcCollections.immutableMap(this)

fun <T> ObjectSet<T>.toSet(): Set<T> = ArcCollections.immutableSet(this)
