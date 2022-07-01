/*
 * Copyright (c) 2020 Orange Bike Labs, LLC.
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