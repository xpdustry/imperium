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
package com.xpdustry.imperium.mindustry.misc

import arc.struct.ObjectMap
import arc.struct.ObjectSet
import arc.struct.Seq
import com.xpdustry.distributor.api.DistributorProvider
import com.xpdustry.distributor.api.collection.MindustryCollections
import com.xpdustry.distributor.api.gui.Action
import com.xpdustry.distributor.api.gui.Pane
import com.xpdustry.distributor.api.gui.WindowManager
import com.xpdustry.distributor.api.gui.transform.Transformer
import com.xpdustry.distributor.api.key.Key
import com.xpdustry.distributor.api.key.MutableKeyContainer
import com.xpdustry.distributor.api.plugin.MindustryPlugin
import com.xpdustry.distributor.api.util.Priority
import com.xpdustry.distributor.api.util.TypeToken
import com.xpdustry.imperium.mindustry.ImperiumPlugin
import java.net.URI
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.reflect.jvm.javaType
import kotlin.reflect.typeOf
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import mindustry.Vars
import mindustry.entities.EntityGroup
import mindustry.gen.Call
import mindustry.gen.Entityc
import mindustry.gen.Player

fun <T> Seq<T>.asList(): List<T> = MindustryCollections.immutableList(this)

fun <T : Entityc> EntityGroup<T>.asList(): List<T> = MindustryCollections.immutableList(this)

fun <K, V> ObjectMap<K, V>.asMap(): Map<K, V> = MindustryCollections.immutableMap(this)

fun <T> ObjectSet<T>.asSet(): Set<T> = MindustryCollections.immutableSet(this)

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

// TODO Clean this shit up
operator fun <P : Pane> Transformer.Context<P>.component1(): P = pane

operator fun <P : Pane> Transformer.Context<P>.component2(): MutableKeyContainer = state

operator fun <P : Pane> Transformer.Context<P>.component3(): Player = viewer

@Suppress("UNCHECKED_CAST")
inline fun <reified T> key(name: String): Key<T> =
    Key.of("imperium", name, TypeToken.of(typeOf<T>().javaType) as TypeToken<T>)

@Suppress("FunctionName")
fun OpenURIAction(uri: URI) = Action { Call.openURI(it.viewer.con(), uri.toString()) }

@Suppress("FunctionName")
fun ShowAction(manager: WindowManager) = Action { manager.create(it).show() }

inline fun <reified E : Any> onEvent(
    priority: Priority = Priority.NORMAL,
    crossinline listener: (E) -> Unit
) =
    DistributorProvider.get().eventBus.subscribe(
        E::class.java,
        priority,
        Vars.mods.getMod(ImperiumPlugin::class.java).main as MindustryPlugin) {
            listener(it)
        }

inline fun <E : Enum<E>> onEvent(
    enum: E,
    priority: Priority = Priority.NORMAL,
    crossinline listener: () -> Unit
) =
    DistributorProvider.get().eventBus.subscribe(
        enum, priority, Vars.mods.getMod(ImperiumPlugin::class.java).main as MindustryPlugin) {
            listener()
        }
