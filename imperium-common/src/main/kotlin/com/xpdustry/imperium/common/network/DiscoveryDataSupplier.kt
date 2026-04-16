// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.network

fun interface DiscoveryDataSupplier {
    fun get(): Discovery.Data
}
