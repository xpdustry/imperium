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
package com.xpdustry.imperium.common.application

import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.Module
import com.xpdustry.imperium.common.inject.SimpleInstanceManager
import com.xpdustry.imperium.common.misc.LoggerDelegate
import kotlin.reflect.KClass

open class SimpleImperiumApplication(module: Module) : ImperiumApplication {

    override val instances: InstanceManager =
        SimpleInstanceManager(module, ApplicationInjectorListener())
    val logger by LoggerDelegate()

    // TODO This should be hidden, but I need to process the instances for the commands for the
    // discord bot
    val listeners = arrayListOf<ImperiumApplication.Listener>()
    private val initialized = arrayListOf<ImperiumApplication.Listener>()

    fun register(listener: ImperiumApplication.Listener) =
        synchronized(listeners) {
            if (listeners.contains(listener)) {
                return
            }
            onListenerRegistration(listener)
        }

    fun register(listener: KClass<out ImperiumApplication.Listener>) {
        var constructor = listener.constructors.find { it.parameters.isEmpty() }
        if (constructor != null) {
            register(constructor.call())
            return
        }
        constructor =
            listener.constructors.find {
                it.parameters.size == 1 &&
                    it.parameters[0].type.classifier == InstanceManager::class
            }
        if (constructor != null) {
            register(constructor.call(instances))
            return
        }
        throw IllegalArgumentException(
            "Cannot find a valid constructor for listener: ${listener.simpleName}")
    }

    protected open fun onListenerRegistration(listener: ImperiumApplication.Listener) {
        logger.debug("Registered listener: {}", listener::class.simpleName)
        listeners.add(listener)
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

    private inner class ApplicationInjectorListener : InstanceManager.Listener {
        override fun onInstanceProvision(instance: Any) {
            if (instance is ImperiumApplication.Listener) {
                this@SimpleImperiumApplication.register(instance)
            }
        }
    }
}
