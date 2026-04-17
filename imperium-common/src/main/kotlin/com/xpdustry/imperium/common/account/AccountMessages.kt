// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.account

import com.xpdustry.imperium.common.message.Message
import kotlinx.serialization.Serializable

@Serializable data class AccountUpdate(val account: Int) : Message

@Serializable
data class AchievementUpdate(val account: Int, val achievement: Achievement, val completed: Boolean) : Message

@Serializable data class MetadataUpdate(val account: Int, val key: String, val value: String?) : Message

@Serializable data class AchievementCompletedMessage(val account: Int, val achievement: Achievement) : Message
