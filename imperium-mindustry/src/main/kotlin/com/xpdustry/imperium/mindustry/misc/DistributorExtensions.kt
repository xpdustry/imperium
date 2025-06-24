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
import com.xpdustry.distributor.api.Distributor
import com.xpdustry.distributor.api.collection.MindustryCollections
import com.xpdustry.distributor.api.component.Component
import com.xpdustry.distributor.api.event.EventSubscription
import com.xpdustry.distributor.api.gui.Action
import com.xpdustry.distributor.api.gui.BiAction
import com.xpdustry.distributor.api.gui.Pane
import com.xpdustry.distributor.api.gui.Window
import com.xpdustry.distributor.api.gui.transform.Transformer
import com.xpdustry.distributor.api.key.Key
import com.xpdustry.distributor.api.key.MutableKeyContainer
import com.xpdustry.distributor.api.plugin.MindustryPlugin
import com.xpdustry.distributor.api.util.Priority
import com.xpdustry.distributor.api.util.TypeToken
import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.mindustry.ImperiumPlugin
import com.xpdustry.imperium.mindustry.translation.gui_error
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.reflect.KClass
import kotlin.reflect.jvm.javaType
import kotlin.reflect.typeOf
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import mindustry.Vars
import mindustry.entities.EntityGroup
import mindustry.gen.Entityc
import mindustry.gen.Player
import org.slf4j.LoggerFactory

fun <T> Seq<T>.asList(): List<T> = MindustryCollections.immutableList(this)

fun <T : Entityc> EntityGroup<T>.asList(): List<T> = MindustryCollections.immutableList(this)

fun <K, V> ObjectMap<K, V>.asMap(): Map<K, V> = MindustryCollections.immutableMap(this)

fun <T> ObjectSet<T>.asSet(): Set<T> = MindustryCollections.immutableSet(this)

// https://stackoverflow.com/a/73494554
suspend fun <T> runMindustryThread(timeout: Duration = 5.seconds, task: () -> T): T =
    withTimeout(timeout) {
        suspendCancellableCoroutine { continuation ->
            Distributor.get()
                .pluginScheduler
                .schedule(Vars.mods.getMod(ImperiumPlugin::class.java).main as MindustryPlugin)
                .async(false)
                .execute { _ -> runCatching(task).fold(continuation::resume, continuation::resumeWithException) }
        }
    }

// TODO Clean this shit up
operator fun <P : Pane> Transformer.Context<P>.component1(): P = pane

operator fun <P : Pane> Transformer.Context<P>.component2(): MutableKeyContainer = state

operator fun <P : Pane> Transformer.Context<P>.component3(): Player = viewer

@Suppress("UNCHECKED_CAST")
inline fun <reified T : Any> key(name: String): Key<T> =
    Key.of("imperium", name, TypeToken.of(typeOf<T>().javaType) as TypeToken<T>)

fun <T : Any> key(name: String, clazz: Class<T>): Key<T> = Key.of("imperium", name, TypeToken.of(clazz))

fun <T : Any> key(name: String, clazz: KClass<T>): Key<T> = Key.of("imperium", name, TypeToken.of(clazz.java))

fun <P : Pane> Transformer<P>.then(transformer: Transformer<P>) = Transformer {
    this@then.transform(it)
    transformer.transform(it)
}

inline fun <reified E : Any> onEvent(
    priority: Priority = Priority.NORMAL,
    crossinline listener: (E) -> Unit,
): EventSubscription =
    Distributor.get().eventBus.subscribe(
        E::class.java,
        priority,
        Vars.mods.getMod(ImperiumPlugin::class.java).main as MindustryPlugin,
    ) {
        listener(it)
    }

inline fun <E : Enum<E>> onEvent(
    enum: E,
    priority: Priority = Priority.NORMAL,
    crossinline listener: () -> Unit,
): EventSubscription =
    Distributor.get().eventBus.subscribe(
        enum,
        priority,
        Vars.mods.getMod(ImperiumPlugin::class.java).main as MindustryPlugin,
    ) {
        listener()
    }

@Suppress("FunctionName")
fun <E : Enum<E>> NavigateAction(key: Key<E>, target: E): Action = Action.with(key, target).then(Window::show)

@Suppress("FunctionName")
fun <E : Enum<E>, P : Pane> NavigationTransformer(key: Key<E>, page: E, transformer: Transformer<P>) = Transformer {
    if (it.state[key] == page) {
        transformer.transform(it)
    }
}

@Suppress("FunctionName")
fun HideAllAndAnnounceAction(message: Component): Action =
    Action.hideAll().then(Action.audience { it.sendAnnouncement(message) })

private val DEFAULT_FAILURE_ACTION =
    BiAction.from<Throwable>(HideAllAndAnnounceAction(gui_error())).then { _, error ->
        LoggerFactory.getLogger(ImperiumPlugin::class.java)
            .error("An unexpected error occurred in a coroutine action", error)
    }

@Suppress("FunctionName")
fun <T : Any> CoroutineAction(
    success: BiAction<T> = BiAction.from(Action.none()),
    failure: BiAction<Throwable> = DEFAULT_FAILURE_ACTION,
    block: suspend (Window) -> T,
): Action = Action { window ->
    ImperiumScope.MAIN.launch {
        try {
            val result = block(window)
            runMindustryThread { success.act(window, result) }
        } catch (e: Throwable) {
            runMindustryThread { failure.act(window, e) }
        }
    }
}

inline fun <reified T : Any> registerDistributorService(plugin: MindustryPlugin, instance: T) {
    @Suppress("UNCHECKED_CAST") val token = TypeToken.of(typeOf<T>().javaType) as TypeToken<T>
    Distributor.get().serviceManager.register(plugin, token, instance, Priority.NORMAL)
}
