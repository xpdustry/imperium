// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.collection

import java.util.LinkedList

class LimitedList<E>(private val limit: Int) : LinkedList<E>() {
    override fun add(element: E): Boolean {
        if (this.size >= limit) {
            removeFirst()
        }
        return super.add(element)
    }
}
