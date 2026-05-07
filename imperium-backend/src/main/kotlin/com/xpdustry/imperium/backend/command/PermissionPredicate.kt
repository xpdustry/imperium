// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.backend.command

import net.dv8tion.jda.api.interactions.Interaction

fun interface PermissionPredicate {
    suspend fun test(interaction: Interaction): Boolean
}
