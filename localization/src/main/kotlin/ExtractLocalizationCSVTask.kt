/*
 * Copyright (c) 2020-2022 The OpenSqueeze Authors. All Rights Reserved.
 * Use of this source code is governed by the GPLv3 license that can be found in the LICENSE file.
 */


@file:Suppress("unused")

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import java.io.File

open class ExtractLocalizationCSVTask : DefaultTask() {
    init {
        outputs.upToDateWhen {
            false
        }
    }

    @TaskAction
    fun performExtraction() {
        val resources = File(project.rootDir, "app/src/main/res")
        val exportDir = File(project.buildDir, "localizationCSV")

        val task = ExtractLocalizationToCSV(resourceDir = resources, exportDir = exportDir)
        task.run()
    }
}
