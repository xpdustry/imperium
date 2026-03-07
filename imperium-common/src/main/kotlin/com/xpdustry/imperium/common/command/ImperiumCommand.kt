// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.command

import com.xpdustry.imperium.common.account.Rank

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class ImperiumCommand(val path: Array<String>, val rank: Rank = Rank.EVERYONE)

val ImperiumCommand.name: String
    get() = path[0]
