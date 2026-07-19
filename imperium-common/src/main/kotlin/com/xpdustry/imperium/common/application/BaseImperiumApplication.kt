// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.application

import com.xpdustry.imperium.common.dependency.DependencyService
import kotlin.reflect.KClass
import org.slf4j.Logger

open class BaseImperiumApplication(private val logger: Logger, modules: (DependencyService.Binder.() -> Unit)? = null) :
    ImperiumApplication {

    private val _listeners = arrayListOf<ImperiumApplication.Listener>()
    private val initialized = arrayListOf<ImperiumApplication.Listener>()
    val listeners: List<ImperiumApplication.Listener> = _listeners

    @Suppress("LeakingThis")
    override val instances = DependencyService {
        bindToProv<ImperiumApplication> { this@BaseImperiumApplication }
        modules?.invoke(this)
    }

    fun register(listener: ImperiumApplication.Listener) {
        if (listeners.contains(listener)) {
            return
        }
        logger.debug("Registered listener: {}", listener::class.simpleName)
        _listeners.add(listener)
    }

    fun register(listener: KClass<out ImperiumApplication.Listener>) {
        @Suppress("UNCHECKED_CAST") register(instances.create(listener as KClass<ImperiumApplication.Listener>))
    }

    fun createAll() {
        for (instance in instances.getAll()) {
            if (instance is ImperiumApplication.Listener) {
                register(instance)
            }
        }
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
