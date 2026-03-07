// SPDX-License-Identifier: GPL-3.0-only
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
