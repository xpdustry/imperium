// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.misc

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class MindustryConfigExtensionsTest {

    @Test
    fun `test mindustry color strip`() {
        Assertions.assertEquals("Hello World", "[#ff0000]Hello World".stripMindustryColors())
        Assertions.assertEquals("Hello [World]", "[#ff0000]Hello [World]".stripMindustryColors())
        Assertions.assertEquals("Hello [[World]", "[#ff0000]Hello [[World]".stripMindustryColors())
        Assertions.assertEquals("Hello World", "[red]Hello []World".stripMindustryColors())
        Assertions.assertEquals("[[[Hello] [World]]", "[][[[Hello] [World]]".stripMindustryColors())
    }
}
