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

import java.util.function.Consumer

abstract class AbstractNucleusApplication : NucleusApplication {
    private val listeners: MutableList<FoundationListener> = ArrayList()

    override fun init() {
        listeners.forEach(Consumer { obj: FoundationListener -> obj.onFoundationInit() })
    }

    override fun exit(cause: NucleusApplication.Cause) {
        listeners.forEach(Consumer { obj: FoundationListener -> obj.onFoundationExit() })
        listeners.clear()
    }

    override fun register(listener: FoundationListener) {
        synchronized(listeners) {
            if (listeners.contains(listener)) {
                return
            }
            listeners.add(listener)
            onRegister(listener)
        }
    }

    protected fun onRegister(listener: FoundationListener?) {}
}
