// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.security

import com.xpdustry.imperium.common.message.Message
import com.xpdustry.imperium.common.misc.MindustryUSID
import com.xpdustry.imperium.common.misc.MindustryUUID
import kotlinx.serialization.Serializable

@Serializable
data class VerificationMessage(
    val account: Int,
    val uuid: MindustryUUID,
    val usid: MindustryUSID,
    val code: Int,
    val response: Boolean = false,
) : Message
