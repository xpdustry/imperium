// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.annotation

interface AnnotationScanner {
    fun scan(instance: Any)

    fun process() = Unit
}
