// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.inject

fun interface InstanceProvider<T> {
    fun create(instances: InstanceManager): T?
}
