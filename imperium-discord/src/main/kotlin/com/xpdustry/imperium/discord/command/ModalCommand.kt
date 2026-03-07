// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.discord.command

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class ModalCommand(val name: String)
