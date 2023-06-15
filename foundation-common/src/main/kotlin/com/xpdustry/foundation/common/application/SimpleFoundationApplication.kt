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
import com.xpdustry.foundation.common.misc.ExitStatus
import com.xpdustry.foundation.common.misc.LoggerDelegate
import kotlin.reflect.KClass

open class SimpleFoundationApplication(common: Module, implementation: Module) : FoundationApplication {

    private val listeners = arrayListOf<FoundationListener>()
    private val injector: Injector = Guice.createInjector(
        Stage.PRODUCTION,
        FoundationAwareModule(Modules.override(common).with(implementation)),
    )

    fun register(listener: FoundationListener) = synchronized(listeners) {
        if (listeners.contains(listener)) {
            return
        }
        onListenerRegistration(listener)
    }

    fun register(listener: KClass<out FoundationListener>) =
        register(injector.getInstance(listener.java))

    fun <T : Any> instance(clazz: KClass<*>): Any =
        injector.getInstance(clazz.java)

    protected open fun onListenerRegistration(listener: FoundationListener) {
        logger.debug("Registered listener: {}", listener::class.simpleName)
        listeners.add(listener)
    }

    override fun init() {
        listeners.forEach(FoundationListener::onFoundationInit)
        logger.info("Foundation has successfully init.")
    }

    override fun exit(status: ExitStatus) {
        listeners.reversed().forEach(FoundationListener::onFoundationExit)
        listeners.clear()
        logger.info("Foundation has successfully exit with status: {}", status)
    }

    inner class FoundationAwareModule(private val module: Module) : KotlinAbstractModule() {
        override fun configure() {
            install(module)
            bind(FoundationApplication::class).instance(this@SimpleFoundationApplication)
            bindProvisionListener(Matchers.any()) {
                val provision = it.provision()
                if (provision is FoundationListener) {
                    this@SimpleFoundationApplication.register(provision)
                }
            }
        }
    }

    companion object {
        private val logger by LoggerDelegate()
    }
}
