// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.command.annotation

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class Flag(val alias: String = "", val repeatable: Boolean = false)
