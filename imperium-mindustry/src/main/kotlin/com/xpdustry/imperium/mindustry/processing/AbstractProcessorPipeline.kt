/*
 * Imperium, the software collection powering the Chaotic Neutral network.
 * Copyright (C) 2024  Xpdustry
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
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
