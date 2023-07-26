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
package com.xpdustry.imperium.common.misc

import org.reactivestreams.Publisher
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono

fun <T> T?.toValueMono(): Mono<T> =
    Mono.justOrEmpty(this)

fun <T : Any> Publisher<T>.toValueMono(): Mono<T> =
    Mono.from(this)

fun <R : Any, T : Throwable> T.toErrorMono(): Mono<R> =
    Mono.error(this)

fun <T> Mono<T>.switchIfEmpty(block: () -> Mono<T>): Mono<T> =
    switchIfEmpty(Mono.defer(block))

fun <T> Mono<T>.doOnEmpty(block: () -> Unit): Mono<T> =
    switchIfEmpty {
        block()
        Mono.empty()
    }

fun <T> Mono<*>.then(block: () -> Mono<T>): Mono<T> =
    then(Mono.defer(block))

fun <T : Any> T?.toValueFlux(): Flux<T> =
    if (this == null) Flux.empty() else Flux.just(this)

fun <T : Any> Publisher<T>.toValueFlux(): Flux<T> =
    Flux.from(this)

fun <T : Any> Iterable<T>.toValueFlux(): Flux<T> =
    Flux.fromIterable(this)

fun <R : Any, T : Throwable> T.toErrorFlux(): Flux<R> =
    Flux.error(this)
