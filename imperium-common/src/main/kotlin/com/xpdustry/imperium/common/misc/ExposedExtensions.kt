// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.misc

import org.jetbrains.exposed.sql.BlobColumnType
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.FieldSet
import org.jetbrains.exposed.sql.IColumnType
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.SqlExpressionBuilder
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.statements.api.ExposedBlob

fun FieldSet.exists(where: SqlExpressionBuilder.() -> Op<Boolean>): Boolean = !selectAll().where(where).empty()

fun Table.mediumblob(name: String): Column<ExposedBlob> = registerColumn(name, MediumBlobColumnType())

private class MediumBlobColumnType : IColumnType<ExposedBlob> by BlobColumnType() {
    override fun sqlType(): String = "MEDIUMBLOB"
}
