/*
 * Imperium, the software collection powering the Xpdustry network.
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
package com.xpdustry.imperium.common.application

import com.xpdustry.imperium.common.version.ImperiumVersion
import java.util.UUID

data class ImperiumMetadata(
    val name: String = "unknown",
    val platform: ImperiumPlatform = ImperiumPlatform.UNKNOWN,
    val version: ImperiumVersion = ImperiumVersion(1, 1, 1),
    val identifier: String = "$name-${UUID.randomUUID()}",
)