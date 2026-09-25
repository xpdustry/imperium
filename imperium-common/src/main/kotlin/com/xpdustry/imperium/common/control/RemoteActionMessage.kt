// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.control

import com.xpdustry.imperium.common.application.ExitStatus
import com.xpdustry.imperium.common.message.Message
import kotlinx.serialization.Serializable

@Serializable
data class RemoteActionMessage(
    val target: String?,
    val action: Action,
    val immediate: Boolean,
    // Older servers ignore this field and fall back to the regular delay
    val waitForEmpty: Boolean = false,
) : Message {
    enum class Action {
        RESTART,
        EXIT,
        CLOSE,
    }
}

fun RemoteActionMessage.Action.toExitStatus() =
    when (this) {
        RemoteActionMessage.Action.EXIT,
        RemoteActionMessage.Action.CLOSE -> ExitStatus.EXIT
        RemoteActionMessage.Action.RESTART -> ExitStatus.RESTART
    }
