// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.backend.command.annotation

import com.xpdustry.imperium.common.permission.Permission

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@MustBeDocumented
annotation class AlsoAllow(val permission: Permission)
