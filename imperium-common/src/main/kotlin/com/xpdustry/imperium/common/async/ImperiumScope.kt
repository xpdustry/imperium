// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.async

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

object ImperiumScope {
    val MAIN = CoroutineScope(Dispatchers.Default + SupervisorJob())
    val IO = CoroutineScope(Dispatchers.IO + SupervisorJob())
}
