// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.config

import com.sksamuel.hoplite.ConfigException
import com.sksamuel.hoplite.ConfigFailure
import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.ExperimentalHoplite
import com.sksamuel.hoplite.KebabCaseParamMapper
import com.sksamuel.hoplite.addPathSource
import com.sksamuel.hoplite.fp.getOrElse
import com.xpdustry.imperium.common.dependency.Named
import com.xpdustry.imperium.common.misc.LoggerDelegate
import java.nio.file.Path

object ImperiumConfigProvider {
    private val logger by LoggerDelegate()

    @OptIn(ExperimentalHoplite::class)
    fun loadConfig(@Named("directory") directory: Path): ImperiumConfig =
        ConfigLoaderBuilder.empty()
            .withClassLoader(ImperiumConfigProvider::class.java.classLoader)
            .addDefaultDecoders()
            .addDefaultPreprocessors()
            .addDefaultParamMappers()
            .addParameterMapper(KebabCaseParamMapper)
            .addDefaultParsers()
            .addPathSource(directory.resolve("config.yaml"), optional = true, allowEmpty = true)
            .addDecoder(ColorDecoder())
            .strict()
            .withReport()
            .withReportPrintFn(logger::debug)
            .withExplicitSealedTypes("_type")
            .build()
            .loadConfig<ImperiumConfig>()
            .getOrElse {
                if (it is ConfigFailure.UndefinedTree) ImperiumConfig() else throw ConfigException(it.description())
            }
}
