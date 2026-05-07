// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.backend.command

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class ModalCommand(val name: String)
