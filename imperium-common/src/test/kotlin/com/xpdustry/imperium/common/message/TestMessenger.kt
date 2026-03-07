// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.message

import kotlin.reflect.KClass

class TestMessenger : MessageService {
    override suspend fun <M : Message> broadcast(message: M, local: Boolean) = Unit

    override fun <M : Message> subscribe(type: KClass<M>, subscriber: MessageService.Subscriber<M>) =
        MessageService.Subscriber.Handle {}
}
