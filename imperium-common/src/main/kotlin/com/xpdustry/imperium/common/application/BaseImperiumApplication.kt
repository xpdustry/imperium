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
package com.xpdustry.imperium.common.application

import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.MutableInstanceManager
import com.xpdustry.imperium.common.inject.SimpleMutableInstanceManager
import kotlin.reflect.KClass
import org.slf4j.Logger

open class BaseImperiumApplication(private val logger: Logger) : ImperiumApplication {

    private val _listeners = arrayListOf<ImperiumApplication.Listener>()
    private val initialized = arrayListOf<ImperiumApplication.Listener>()
    val listeners: List<ImperiumApplication.Listener> = _listeners
    override val instances: MutableInstanceManager = SimpleMutableInstanceManager {
        if (it is ImperiumApplication.Listener) register(it)
    }

    fun register(listener: ImperiumApplication.Listener) {
        if (listeners.contains(listener)) {
            return
        }
        logger.debug("Registered listener: {}", listener::class.simpleName)
        _listeners.add(listener)
    }

    fun register(listener: KClass<out ImperiumApplication.Listener>) {
        var constructor = listener.constructors.find { it.parameters.isEmpty() }
        if (constructor != null) {
            register(constructor.call())
            return
        }
        constructor =
            listener.constructors.find {
                it.parameters.size == 1 && it.parameters[0].type.classifier == InstanceManager::class
            }
        if (constructor != null) {
            register(constructor.call(instances))
            return
        }
        throw IllegalArgumentException("Cannot find a valid constructor for listener: ${listener.simpleName}")
    }

    override fun init() {
        try {
            for (listener in listeners) {
                listener.onImperiumInit()
                initialized += listener
            }
            logger.info("Imperium has successfully initialized.")
        } catch (e: Exception) {
            logger.error("Imperium failed to init.", e)
            exit(ExitStatus.INIT_FAILURE)
        }
    }

    override fun exit(status: ExitStatus) {
        initialized.reversed().forEach {
            try {
                it.onImperiumExit()
            } catch (e: Exception) {
                logger.error("Error while exiting listener {}", it::class.simpleName, e)
            }
        }
        logger.info("Imperium has exit with status: {}", status)
    }
}
