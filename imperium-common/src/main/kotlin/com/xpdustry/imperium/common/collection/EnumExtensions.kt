// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.collection

import java.util.EnumSet

inline fun <reified T : Enum<T>> enumSetOf(vararg elements: T): Set<T> =
    EnumSet.noneOf(T::class.java).apply { addAll(elements) }

inline fun <reified T : Enum<T>> enumSetAllOf(): Set<T> = EnumSet.allOf(T::class.java)
