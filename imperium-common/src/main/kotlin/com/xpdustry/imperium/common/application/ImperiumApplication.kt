// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.application

import com.xpdustry.imperium.common.dependency.DependencyService

interface ImperiumApplication {

    val instances: DependencyService

    fun init()

    fun exit(status: ExitStatus)

    interface Listener {
        fun onImperiumInit() = Unit

        fun onImperiumExit() = Unit
    }
}
