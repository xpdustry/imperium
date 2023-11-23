/*
 * Imperium, the software collection powering the Xpdustry network.
 * Copyright (C) 2023  Xpdustry
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
package com.xpdustry.imperium.common.snowflake

import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.misc.LoggerDelegate
import de.mkammerer.snowflakeid.SnowflakeIdGenerator
import de.mkammerer.snowflakeid.options.Options
import de.mkammerer.snowflakeid.structure.Structure
import de.mkammerer.snowflakeid.time.MonotonicTimeSource
import java.time.Duration
import java.time.Instant

typealias Snowflake = Long

val Snowflake.timestamp: Instant
    get() =
        Instant.ofEpochMilli(
                this shr
                    (SimpleSnowflakeGenerator.STRUCTURE.generatorBits +
                        SimpleSnowflakeGenerator.STRUCTURE.sequenceBits))
            .plus(SimpleSnowflakeGenerator.IMPERIUM_EPOCH_OFFSET)

interface SnowflakeGenerator {
    fun generate(): Snowflake
}

class SimpleSnowflakeGenerator(config: ImperiumConfig) : SnowflakeGenerator {

    private val generator =
        SnowflakeIdGenerator.createCustom(
            config.generatorId.toLong(),
            MonotonicTimeSource(IMPERIUM_EPOCH),
            STRUCTURE,
            Options(Options.SequenceOverflowStrategy.SPIN_WAIT),
        )

    init {
        if (config.generatorId == 0) {
            logger.warn("The generator-id is 0, this config needs an explicit value.")
        }
    }

    override fun generate(): Snowflake = generator.next()

    companion object {
        // The creation date of the chaotic neutral server
        internal val IMPERIUM_EPOCH = Instant.ofEpochMilli(1543879632378L)
        internal val IMPERIUM_EPOCH_OFFSET = Duration.between(Instant.EPOCH, IMPERIUM_EPOCH)
        internal val STRUCTURE = Structure(51, 8, 4)
        private val logger by LoggerDelegate()
    }
}
