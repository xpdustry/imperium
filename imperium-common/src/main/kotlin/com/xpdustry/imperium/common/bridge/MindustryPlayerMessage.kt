// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.bridge

import com.xpdustry.imperium.common.message.Message
import kotlinx.serialization.Serializable

@Serializable
class MindustryPlayerMessage(val server: String, val player: String, val action: Action) : Message {
    @Serializable
    sealed interface Action {
        @Serializable data object Join : Action

        @Serializable data object Quit : Action

        @Serializable data class Chat(val message: String) : Action
    }
}
