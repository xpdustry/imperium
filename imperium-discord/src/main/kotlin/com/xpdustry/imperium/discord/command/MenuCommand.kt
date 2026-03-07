// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.discord.command

import com.xpdustry.imperium.common.account.Rank

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class MenuCommand(val name: String, val rank: Rank = Rank.EVERYONE)
