// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.mindustry.processing

import com.xpdustry.distributor.api.util.Priority
import com.xpdustry.imperium.common.misc.logger

abstract class AbstractProcessorPipeline<I : Any, O : Any>(name: String) : ProcessorPipeline<I, O> {

    private val logger = logger("processor-pipeline-$name")

    protected val processors: List<Processor<I, O>>
        get() = _processors.map { it.processor }

    protected fun processor(name: String): Processor<I, O>? = _processors.firstOrNull { it.name == name }?.processor

    private val _processors = mutableListOf<ProcessorWithData>()

    override fun register(name: String, priority: Priority, processor: Processor<I, O>) {
        if (_processors.any { it.name == name }) {
            throw IllegalArgumentException("Processor with name $name already registered")
        }
        _processors.add(ProcessorWithData(processor, name, priority))
        _processors.sortBy { it.priority }
        logger.debug("Registered processor {} with priority {}", name, priority)
    }

    private inner class ProcessorWithData(val processor: Processor<I, O>, val name: String, val priority: Priority)
}
