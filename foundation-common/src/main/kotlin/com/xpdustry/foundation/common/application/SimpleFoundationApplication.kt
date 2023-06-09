/*
 * Foundation, the software collection powering the Xpdustry network.
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
package com.xpdustry.foundation.common.application

import com.google.inject.Guice
import com.google.inject.Injector
import com.google.inject.Module
import com.google.inject.Stage
import com.google.inject.matcher.Matchers
import com.google.inject.util.Modules
import com.xpdustry.foundation.common.misc.ExitCode
import kotlin.reflect.KClass
import org.slf4j.Logger
import org.slf4j.LoggerFactory

val logger: Logger = LoggerFactory.getLogger("Foundation")

open class SimpleFoundationApplication(common: Module, implementation: Module) : FoundationApplication {

    private val listeners = arrayListOf<FoundationListener>()
    private val injector: Injector = Guice.createInjector(
        Stage.PRODUCTION,
        FoundationAwareModule(Modules.override(common).with(implementation))
    )

    fun register(listener: FoundationListener) = synchronized(listeners) {
        if (listeners.contains(listener)) {
            return
        }
        onListenerRegistration(listener)
    }

    fun register(listener: KClass<out FoundationListener>) = register(getInstance(listener.java))

    protected fun onListenerRegistration(listener: FoundationListener) {
        logger.info("Registered listener: {}", listener)
        listeners.add(listener)
    }

    override fun init() {
        listeners.forEach(FoundationListener::onFoundationInit)
    }

    override fun exit(code: ExitCode) {
        listeners.forEach(FoundationListener::onFoundationExit)
        listeners.clear()
    }

    override fun <T : Any> getInstance(type: Class<T>): T =
        injector.getInstance(type)

    inner class FoundationAwareModule(private val module: Module) : KotlinAbstractModule() {
        override fun configure() {
            install(module)
            bind(FoundationApplication::class.java).toInstance(this@SimpleFoundationApplication)
            bindProvisionListener(Matchers.any()) {
                val provision = it.provision()
                if (provision is FoundationListener) {
                    this@SimpleFoundationApplication.register(provision)
                }
            }
        }
    }
}
