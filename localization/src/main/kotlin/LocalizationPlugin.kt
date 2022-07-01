/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */


@file:Suppress("unused")

import org.gradle.api.Plugin
import org.gradle.api.Project

import org.gradle.kotlin.dsl.*

class LocalizationPlugin : Plugin<Project> {

    override fun apply(project: Project): Unit = project.run {
        tasks {
            register("extractLocalizationCSV", ExtractLocalizationCSVTask::class.java)
        }
    }
}