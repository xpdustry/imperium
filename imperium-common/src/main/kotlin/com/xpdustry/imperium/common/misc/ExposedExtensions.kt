// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.misc

import org.jetbrains.exposed.v1.core.BlobColumnType
import org.jetbrains.exposed.v1.core.Column
import org.jetbrains.exposed.v1.core.FieldSet
import org.jetbrains.exposed.v1.core.IColumnType
import org.jetbrains.exposed.v1.core.Op
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.core.statements.api.ExposedBlob
import org.jetbrains.exposed.v1.jdbc.selectAll

fun FieldSet.exists(where: () -> Op<Boolean>): Boolean = !selectAll().where(where).empty()

fun Table.mediumblob(name: String): Column<ExposedBlob> = registerColumn(name, MediumBlobColumnType())

private class MediumBlobColumnType : IColumnType<ExposedBlob> by BlobColumnType() {
    override fun sqlType(): String = "MEDIUMBLOB"
}
