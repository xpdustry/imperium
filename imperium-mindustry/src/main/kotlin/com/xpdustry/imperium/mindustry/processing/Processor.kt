// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.processing

fun interface Processor<I : Any, O : Any> {
    suspend fun process(context: I): O
}
