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

import com.xpdustry.imperium.common.async.ImperiumScope
import kotlin.reflect.KClass
import kotlin.time.Duration
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class TestMessenger : Messenger {
    override suspend fun publish(message: Message, local: Boolean): Boolean = true

    override suspend fun <R : Message> request(message: Message, timeout: Duration, responseKlass: KClass<R>): R? = null

    override fun <M : Message> consumer(type: KClass<M>, listener: Messenger.ConsumerListener<M>): Job =
        ImperiumScope.MAIN.launch { while (isActive) delay(1000L) }

    override fun <M : Message, R : Message> function(type: KClass<M>, function: Messenger.FunctionListener<M, R>): Job =
        ImperiumScope.MAIN.launch { while (isActive) delay(1000L) }
}
