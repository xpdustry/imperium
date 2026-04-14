// SPDX-License-Identifier: GPL-3.0-only
package com.xpdustry.imperium.common.config

import com.sksamuel.hoplite.ConfigException
import com.sksamuel.hoplite.ConfigFailure
import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.ExperimentalHoplite
import com.sksamuel.hoplite.KebabCaseParamMapper
import com.sksamuel.hoplite.addPathSource
import com.sksamuel.hoplite.fp.getOrElse
import com.xpdustry.imperium.common.inject.InstanceManager
import com.xpdustry.imperium.common.inject.InstanceProvider
import com.xpdustry.imperium.common.inject.get
import com.xpdustry.imperium.common.misc.LoggerDelegate
import java.nio.file.Path

object ImperiumConfigProvider : InstanceProvider<ImperiumConfig> {
    private val logger by LoggerDelegate()

    @OptIn(ExperimentalHoplite::class)
    override fun create(instances: InstanceManager) =
        ConfigLoaderBuilder.empty()
            .withClassLoader(ImperiumConfigProvider::class.java.classLoader)
            .addDefaultDecoders()
            .addDefaultPreprocessors()
            .addDefaultParamMappers()
            .addParameterMapper(KebabCaseParamMapper)
            .addDefaultParsers() // YamlParser is loaded via ServiceLoader here
            .addPathSource(instances.get<Path>("directory").resolve("config.yaml"), optional = true, allowEmpty = true)
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
