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
package com.xpdustry.imperium.common.message

import kotlin.reflect.KClass

interface Message

interface MessageService {
    suspend fun <M : Message> broadcast(message: M, local: Boolean = false)

    fun <M : Message> subscribe(type: KClass<M>, subscriber: Subscriber<M>): Subscriber.Handle

    fun interface Subscriber<M : Message> {
        suspend fun onMessage(message: M)

        fun interface Handle {
            fun cancel()
        }
    }

    data object Noop : MessageService {
        override suspend fun <M : Message> broadcast(message: M, local: Boolean) = Unit

        override fun <M : Message> subscribe(type: KClass<M>, subscriber: Subscriber<M>) = Subscriber.Handle {}
    }
}

inline fun <reified M : Message> MessageService.subscribe(subscriber: MessageService.Subscriber<M>) =
    subscribe(M::class, subscriber)
