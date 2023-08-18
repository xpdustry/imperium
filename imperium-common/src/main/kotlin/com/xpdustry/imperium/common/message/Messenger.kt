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
package com.xpdustry.imperium.common.message

import kotlinx.coroutines.Job
import kotlin.reflect.KClass

interface Messenger {
    suspend fun publish(message: Message)
    fun <M : Message> subscribe(type: KClass<M>, listener: Listener<M>): Job
    fun interface Listener<M : Message> {
        suspend fun onMessage(message: M)
    }
}

inline fun <reified M : Message> Messenger.subscribe(listener: Messenger.Listener<M>) = subscribe(M::class, listener)
