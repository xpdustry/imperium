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
package com.xpdustry.imperium.mindustry.ui.action

import com.xpdustry.imperium.mindustry.ui.View
import com.xpdustry.imperium.mindustry.ui.state.State
import java.net.URI
import java.util.function.Consumer
import mindustry.Vars
import mindustry.gen.Call

fun interface Action {
    fun accept(view: View)

    fun then(after: Action): Action {
        return Action { view ->
            accept(view)
            after.accept(view)
        }
    }

    fun <T : Any> asBiAction(): BiAction<T> {
        return BiAction { view: View, _: T -> accept(view) }
    }

    companion object {
        fun none(): Action {
            return Action {}
        }

        fun open(): Action {
            return Action { obj: View -> obj.open() }
        }

        fun open(consumer: Consumer<State>): Action {
            return Action { view: View ->
                consumer.accept(view.state)
                view.open()
            }
        }

        fun close(): Action {
            return Action { it.close() }
        }

        fun info(message: String): Action {
            return Action { view -> Call.infoMessage(view.viewer.con(), message) }
        }

        fun uri(uri: URI): Action {
            return Action { view -> Call.openURI(view.viewer.con(), uri.toString()) }
        }

        fun run(runnable: Runnable): Action {
            return Action { _ -> runnable.run() }
        }

        fun command(name: String, vararg arguments: String): Action {
            val builder = StringBuilder(name.length + 1 + arguments.size * 4)
            builder.append('/').append(name)
            for (argument in arguments) {
                builder.append(' ').append(argument)
            }
            val input = builder.toString()
            return Action { view: View -> Vars.netServer.clientCommands.handleMessage(input, view.viewer) }
        }
    }
}
