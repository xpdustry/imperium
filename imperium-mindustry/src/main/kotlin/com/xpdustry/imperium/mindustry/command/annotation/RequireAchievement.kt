// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.command.annotation

import com.xpdustry.imperium.common.account.Achievement

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class RequireAchievement(val achievement: Achievement)
