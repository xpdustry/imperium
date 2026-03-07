// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.message

import com.xpdustry.imperium.common.application.ImperiumApplication
import com.xpdustry.imperium.common.misc.LoggerDelegate
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlinx.coroutines.Job

class NoopMessenger : Messenger, ImperiumApplication.Listener {
    override fun onImperiumInit() {
        LOGGER.warn("Using NoopMessenger, this is not recommended for production")
    }

    override suspend fun publish(message: Message, local: Boolean): Boolean = false

    override suspend fun <R : Message> request(message: Message, timeout: Duration, responseKlass: KClass<R>): R? = null

    override fun <M : Message> consumer(type: KClass<M>, listener: Messenger.ConsumerListener<M>): Job = Job()

    override fun <M : Message, R : Message> function(type: KClass<M>, function: Messenger.FunctionListener<M, R>): Job =
        Job()

    companion object {
        private val LOGGER by LoggerDelegate()
    }
}
