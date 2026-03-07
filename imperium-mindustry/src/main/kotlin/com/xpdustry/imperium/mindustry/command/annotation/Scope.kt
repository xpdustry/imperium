// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.command.annotation

import com.xpdustry.imperium.common.content.MindustryGamemode

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class Scope(vararg val gamemodes: MindustryGamemode)
