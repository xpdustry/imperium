// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.async

import com.xpdustry.imperium.common.misc.logger
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

const val IMPERIUM_SCOPE = "imperium"

fun createImperiumScope(): CoroutineScope =
    CoroutineScope(
        SupervisorJob() +
            Dispatchers.Default +
            CoroutineName(IMPERIUM_SCOPE) +
            CoroutineExceptionHandler { context, error ->
                logger("ImperiumCoroutineScope").error("Unhandled exception in coroutine context {}", context, error)
            }
    )
