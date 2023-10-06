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
package com.xpdustry.imperium.common.database.snowflake

import com.xpdustry.imperium.common.async.ImperiumScope
import com.xpdustry.imperium.common.config.ImperiumConfig
import com.xpdustry.imperium.common.database.snowflake.SimpleSnowflakeGenerator.Companion.IMPERIUM_EPOCH_OFFSET
import com.xpdustry.imperium.common.misc.LoggerDelegate
import de.mkammerer.snowflakeid.SnowflakeIdGenerator
import de.mkammerer.snowflakeid.options.Options
import de.mkammerer.snowflakeid.structure.Structure
import de.mkammerer.snowflakeid.time.MonotonicTimeSource
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant

typealias Snowflake = Long

// Given the fact that the snowflake is structured as (timestamp, generatorId, sequenceId),
// we can extract the timestamp by shifting the snowflake to the right by the number of bits used by the generatorId
// and sequenceId, and then converting it to an Instant.
val Snowflake.timestamp: Instant
    get() = Instant.ofEpochMilli(this shr (SimpleSnowflakeGenerator.STRUCTURE.generatorBits + SimpleSnowflakeGenerator.STRUCTURE.sequenceBits)).plus(IMPERIUM_EPOCH_OFFSET)

interface SnowflakeGenerator {
    suspend fun generate(): Snowflake
}

class SimpleSnowflakeGenerator(config: ImperiumConfig) : SnowflakeGenerator {

    private val generator = SnowflakeIdGenerator.createCustom(
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

    override suspend fun generate(): Snowflake =
        withContext(ImperiumScope.MAIN.coroutineContext) { generator.next() }

    companion object {
        // The creation date of the chaotic neutral server
        internal val IMPERIUM_EPOCH = Instant.ofEpochMilli(1543879632378L)
        internal val IMPERIUM_EPOCH_OFFSET = Duration.between(Instant.EPOCH, IMPERIUM_EPOCH)
        internal val STRUCTURE = Structure(51, 8, 4)
        private val logger by LoggerDelegate()
    }
}
