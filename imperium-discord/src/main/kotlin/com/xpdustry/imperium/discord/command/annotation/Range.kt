// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.discord.command.annotation

// TODO
//   Using AnnotationTarget.TYPE CRASHES, AND IT HAS BEEN AN ISSUE FOR 3 YEARS!!
//   Could be fixed with a custom proxy via java (https://stackoverflow.com/a/13324487)
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.VALUE_PARAMETER)
annotation class Range(val min: String = "", val max: String = "")
