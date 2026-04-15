// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.dependency

@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.RUNTIME)
annotation class Named(val value: String)
