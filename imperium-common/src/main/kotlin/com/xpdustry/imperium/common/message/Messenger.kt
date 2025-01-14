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
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Job

interface Messenger {

    suspend fun publish(message: Message, local: Boolean = false): Boolean

    suspend fun <R : Message> request(message: Message, timeout: Duration, responseKlass: KClass<R>): R?

    fun <M : Message> consumer(type: KClass<M>, listener: ConsumerListener<M>): Job

    fun <M : Message, R : Message> function(type: KClass<M>, function: FunctionListener<M, R>): Job

    fun interface ConsumerListener<M : Message> {
        suspend fun onMessage(message: M)
    }

    fun interface FunctionListener<M : Message, R : Message> {
        suspend fun onMessage(message: M): R?
    }
}

inline fun <reified M : Message> Messenger.consumer(listener: Messenger.ConsumerListener<M>) =
    consumer(M::class, listener)

inline fun <reified M : Message, reified R : Message> Messenger.function(function: Messenger.FunctionListener<M, R>) =
    function(M::class, function)

suspend inline fun <reified R : Message> Messenger.request(message: Message, timeout: Duration = 3.seconds) =
    request(message, timeout, R::class)
