// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.processing

import com.xpdustry.distributor.api.util.Priority

interface ProcessorPipeline<I : Any, O : Any> {
    fun register(name: String, priority: Priority = Priority.NORMAL, processor: Processor<I, O>)

    suspend fun pump(context: I): O
}
