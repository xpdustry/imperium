// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.security

import com.xpdustry.imperium.common.serialization.SerializableInetAddress
import kotlinx.serialization.Serializable

@Serializable
sealed interface Identity {
    val name: String

    @Serializable
    data class Mindustry(
        override val name: String,
        val uuid: String,
        val usid: String,
        val address: SerializableInetAddress,
        val displayName: String = name,
    ) : Identity

    @Serializable data class Discord(override val name: String, val id: Long) : Identity

    @Serializable data class Server(override val name: String) : Identity
}
