/*
 * Foundation, the software collection powering the Xpdustry network.
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
package com.xpdustry.foundation.common.database

import java.util.function.UnaryOperator
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

interface EntityManager<I, E : Entity<I>> {
    fun save(entity: E): Mono<Void>
    fun saveAll(entities: Iterable<E>): Mono<Void>
    fun findById(id: I): Mono<E>
    fun findAll(): Flux<E>
    fun exists(entity: E): Mono<Boolean>
    fun count(): Mono<Long>
    fun deleteById(id: I): Mono<Void>
    fun deleteAll(): Mono<Void>
    fun deleteAll(entities: Iterable<E>): Mono<Void>
    fun updateIfPresent(id: I, updater: UnaryOperator<E>): Mono<Void> =
        findById(id).map(updater).flatMap { entity: E -> save(entity) }
}
