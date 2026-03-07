// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.inject

import kotlin.reflect.KClass

data class InstanceKey<T : Any>(val clazz: KClass<T>, val name: String)
