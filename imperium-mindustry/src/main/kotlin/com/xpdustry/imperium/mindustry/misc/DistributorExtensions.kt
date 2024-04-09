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
import com.xpdustry.distributor.DistributorProvider
import com.xpdustry.distributor.collection.ArcCollections
import com.xpdustry.distributor.plugin.MindustryPlugin
import com.xpdustry.imperium.mindustry.ImperiumPlugin
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import mindustry.Vars
import mindustry.entities.EntityGroup
import mindustry.gen.Entityc

fun <T> Seq<T>.asList(): List<T> = ArcCollections.immutableList(this)

fun <T : Entityc> EntityGroup<T>.asList(): List<T> = ArcCollections.immutableList(this)

fun <K, V> ObjectMap<K, V>.asMap(): Map<K, V> = ArcCollections.immutableMap(this)

fun <T> ObjectSet<T>.asSet(): Set<T> = ArcCollections.immutableSet(this)

// https://stackoverflow.com/a/73494554
suspend fun <T> runMindustryThread(timeout: Duration = 5.seconds, task: () -> T): T =
    withTimeout(timeout) {
        suspendCancellableCoroutine { continuation ->
            DistributorProvider.get()
                .pluginScheduler
                .schedule(Vars.mods.getMod(ImperiumPlugin::class.java).main as MindustryPlugin)
                .async(false)
                .execute { _ ->
                    runCatching(task).fold(continuation::resume, continuation::resumeWithException)
                }
        }
    }
