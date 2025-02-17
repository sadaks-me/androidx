/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.baselineprofile.gradle.apptarget

import androidx.baselineprofile.gradle.utils.AgpPlugin
import androidx.baselineprofile.gradle.utils.AgpPluginId
import androidx.baselineprofile.gradle.utils.BUILD_TYPE_BASELINE_PROFILE_PREFIX
import androidx.baselineprofile.gradle.utils.MAX_AGP_VERSION_REQUIRED
import androidx.baselineprofile.gradle.utils.MIN_AGP_VERSION_REQUIRED
import androidx.baselineprofile.gradle.utils.createExtendedBuildTypes
import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * This is the app target plugin for baseline profile generation. In order to generate baseline
 * profiles three plugins are needed: one is applied to the app or the library that should consume
 * the baseline profile when building (consumer), one is applied to the module that should supply
 * the under test app (app target) and the last one is applied to a test module containing the ui
 * test that generate the baseline profile on the device (producer).
 */
class BaselineProfileAppTargetPlugin : Plugin<Project> {
    override fun apply(project: Project) = BaselineProfileAppTargetAgpPlugin(project).onApply()
}

private class BaselineProfileAppTargetAgpPlugin(private val project: Project) : AgpPlugin(
    project = project,
    supportedAgpPlugins = setOf(
        AgpPluginId.ID_ANDROID_APPLICATION_PLUGIN,
        AgpPluginId.ID_ANDROID_LIBRARY_PLUGIN
    ),
    minAgpVersion = MIN_AGP_VERSION_REQUIRED,
    maxAgpVersion = MAX_AGP_VERSION_REQUIRED
) {
    override fun onAgpPluginNotFound(pluginIds: Set<AgpPluginId>) {

        // If no supported plugin was found throw an exception.
        throw IllegalStateException(
            """
            The module ${project.name} does not have the `com.android.application` plugin
            applied. The `androidx.baselineprofile.apptarget` plugin supports only
            android application modules. Please review your build.gradle to ensure this
            plugin is applied to the correct module.
            """.trimIndent()
        )
    }

    override fun onAgpPluginFound(pluginIds: Set<AgpPluginId>) {

        // If the library plugin was found throw an exception. It's possible the developer meant
        // to generate a baseline profile for a library and we can give further information.
        if (pluginIds.contains(AgpPluginId.ID_ANDROID_LIBRARY_PLUGIN)) {
            throw IllegalStateException(
                """
            The module ${project.name} does not have the `com.android.application` plugin
            but has the `com.android.library` plugin. If you're trying to generate a
            baseline profile for a library, you'll need to apply the
            `androidx.baselineprofile.apptarget` to an android application that
            has the `com.android.application` plugin applied. This should be a sample app
            running the code of the library for which you want to generate the profile.
            Please review your build.gradle to ensure this plugin is applied to the
            correct module.
            """.trimIndent()
            )
        }

        // Otherwise, just log the plugin was applied.
        project
            .logger
            .debug("[BaselineProfileAppTargetPlugin] afterEvaluate check: app plugin was applied")
    }

    override fun onApplicationFinalizeDsl(extension: ApplicationExtension) {
        // Creates baseline profile build types extending the currently existing ones.
        // They're named `<BUILD_TYPE_BASELINE_PROFILE_PREFIX><originalBuildTypeName>`.
        createExtendedBuildTypes(
            project = project,
            extensionBuildTypes = extension.buildTypes,
            newBuildTypePrefix = BUILD_TYPE_BASELINE_PROFILE_PREFIX,
            filterBlock = {
                // Create baseline profile build types only for non debuggable builds.
                !it.isDebuggable
            },
            configureBlock = {
                isJniDebuggable = false
                isDebuggable = false
                isMinifyEnabled = false
                isShrinkResources = false
                isProfileable = true
                signingConfig = extension.buildTypes.getByName("debug").signingConfig
                enableAndroidTestCoverage = false
                enableUnitTestCoverage = false
            }
        )
    }
}
