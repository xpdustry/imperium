/*
 * Imperium, the software collection powering the Chaotic Neutral network.
 * Copyright (C) 2024  Xpdustry
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
package com.xpdustry.imperium.common.security

import com.xpdustry.imperium.common.serialization.SerializableInetAddress
import kotlinx.serialization.Serializable

@Deprecated("Replace with relevant data directly instead")
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
