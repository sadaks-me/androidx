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

package androidx.baselineprofile.gradle.producer

import androidx.baselineprofile.gradle.configuration.ConfigurationManager
import androidx.baselineprofile.gradle.producer.tasks.CollectBaselineProfileTask
import androidx.baselineprofile.gradle.producer.tasks.InstrumentationTestTaskWrapper
import androidx.baselineprofile.gradle.utils.AgpPlugin
import androidx.baselineprofile.gradle.utils.AgpPluginId
import androidx.baselineprofile.gradle.utils.AndroidTestModuleWrapper
import androidx.baselineprofile.gradle.utils.BUILD_TYPE_BASELINE_PROFILE_PREFIX
import androidx.baselineprofile.gradle.utils.CONFIGURATION_ARTIFACT_TYPE
import androidx.baselineprofile.gradle.utils.CONFIGURATION_NAME_BASELINE_PROFILES
import androidx.baselineprofile.gradle.utils.MAX_AGP_VERSION_REQUIRED
import androidx.baselineprofile.gradle.utils.MIN_AGP_VERSION_REQUIRED
import androidx.baselineprofile.gradle.utils.RELEASE
import androidx.baselineprofile.gradle.utils.camelCase
import androidx.baselineprofile.gradle.utils.createBuildTypeIfNotExists
import androidx.baselineprofile.gradle.utils.createExtendedBuildTypes
import com.android.build.api.dsl.TestBuildType
import com.android.build.api.dsl.TestExtension
import com.android.build.api.variant.TestVariant
import com.android.build.api.variant.TestVariantBuilder
import com.android.build.api.variant.Variant
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * This is the producer plugin for baseline profile generation. In order to generate baseline
 * profiles three plugins are needed: one is applied to the app or the library that should consume
 * the baseline profile when building (consumer), one is applied to the module that should supply
 * the under test app (app target) and the last one is applied to a test module containing the ui
 * test that generate the baseline profile on the device (producer).
 */
class BaselineProfileProducerPlugin : Plugin<Project> {
    override fun apply(project: Project) = BaselineProfileProducerAgpPlugin(project).onApply()
}

private class BaselineProfileProducerAgpPlugin(private val project: Project) : AgpPlugin(
    project = project,
    supportedAgpPlugins = setOf(AgpPluginId.ID_ANDROID_TEST_PLUGIN),
    minAgpVersion = MIN_AGP_VERSION_REQUIRED,
    maxAgpVersion = MAX_AGP_VERSION_REQUIRED
) {

    private val baselineProfileExtension = BaselineProfileProducerExtension.register(project)
    private val configurationManager = ConfigurationManager(project)

    // This maps all the extended build types to the original ones. Note that release does not
    // exist by default so we need to create nonMinifiedRelease and map it manually to `release`.
    private val nonObfuscatedReleaseName =
        camelCase(BUILD_TYPE_BASELINE_PROFILE_PREFIX, RELEASE)
    private val extendedTypeToOriginalTypeMapping =
        mutableMapOf(nonObfuscatedReleaseName to RELEASE)

    override fun onAgpPluginFound(pluginIds: Set<AgpPluginId>) {
        project
            .logger
            .debug("[BaselineProfileProducerPlugin] afterEvaluate check: app plugin was applied")
    }

    override fun onAgpPluginNotFound(pluginIds: Set<AgpPluginId>) {
        throw IllegalStateException(
            """
    The module ${project.name} does not have the `com.android.test` plugin applied. Currently,
    the `androidx.baselineprofile.producer` plugin supports only android test modules. In future this
    plugin will also support library modules (https://issuetracker.google.com/issue?id=259737450).
    Please review your build.gradle to ensure this plugin is applied to the correct module.
            """.trimIndent()
        )
    }

    override fun onBeforeFinalizeDsl() {

        // We need the instrumentation apk to run as a separate process
        AndroidTestModuleWrapper(project).setSelfInstrumenting(true)
    }

    override fun onTestFinalizeDsl(extension: TestExtension) {

        // Creates the new build types to match the app target. All the existing build types beside
        // `debug`, that is the default one, are added manually in the configuration so we can
        // assume they've been added for the purpose of generating baseline profiles. We don't
        // need to create a nonMinified build type from `debug` since there will be no matching
        // configuration with the app target module.

        // The test build types need to be debuggable and have the same singing config key to
        // be installed. We also disable the test coverage tracking since it's not important
        // here.
        val configureBlock: TestBuildType.() -> (Unit) = {
            isDebuggable = true
            enableAndroidTestCoverage = false
            enableUnitTestCoverage = false
            signingConfig = extension.buildTypes.getByName("debug").signingConfig

            // TODO: The matching fallback is causing a circular dependency when app target plugin
            //  is not applied. Normally this is not used, but if an app defines only the consumer
            //  plugin the `nonMinified` build won't exist. If the provider points to it, the
            //  matching fallback will kick in and will use the `release` build instead of the
            //  `nonMinifiedRelease`. When this happens, since  we depend on
            //  `mergeArtReleaseProfile` to ensure that a profile is copied is the baseline profile
            //  src sets before the build is complete, it will trigger a circular task dependency:
            //      collectNonMinifiedReleaseBaselineProfile ->
            //          connectedNonMinifiedReleaseAndroidTest ->
            //              packageRelease ->
            //                  compileReleaseArtProfile ->
            //                      mergeReleaseArtProfile ->
            //                          copyReleaseBaselineProfileIntoSrc ->
            //                              mergeReleaseBaselineProfile ->
            //                                  collectNonMinifiedReleaseBaselineProfile
            //  Note that the error is expected but we should handle it gracefully with proper
            //  explanation. (b/272851616)
            matchingFallbacks += listOf(RELEASE)
        }

        // The variant names are used by the test module to request a specific apk artifact to
        // the under test app module (using configuration attributes). This is all handled by
        // the com.android.test plugin, as long as both modules have the same variants.
        // Unfortunately the test module cannot determine which variants are present in the
        // under test app module. As a result we need to replicate the same build types and
        // flavors, so that the same variant names are created.
        createExtendedBuildTypes(
            project = project,
            extensionBuildTypes = extension.buildTypes,
            newBuildTypePrefix = BUILD_TYPE_BASELINE_PROFILE_PREFIX,
            extendedBuildTypeToOriginalBuildTypeMapping = extendedTypeToOriginalTypeMapping,
            configureBlock = configureBlock,
            filterBlock = {
                // All the build types that have been added to the test module should be
                // extended. This is because we can't know here which ones are actually
                // release in the under test module. We can only exclude debug for sure.
                it.name != "debug"
            }
        )
        createBuildTypeIfNotExists(
            project = project,
            extensionBuildTypes = extension.buildTypes,
            buildTypeName = nonObfuscatedReleaseName,
            configureBlock = configureBlock
        )
    }

    override fun onTestBeforeVariants(variantBuilder: TestVariantBuilder) {

        // Makes sure that only the non obfuscated build type variant selected is enabled
        variantBuilder.enable = variantBuilder.buildType in extendedTypeToOriginalTypeMapping.keys
    }

    override fun onTestVariants(variant: TestVariant) {

        // Creates all the configurations, one per variant for the newly created build type.
        // Note that for this version of the plugin is not possible to rely entirely on the variant
        // api so the actual creation of the tasks is postponed to be executed when all the
        // agp tasks have been created, using the old api.

        // Creating configurations only for the extended build types.
        if (variant.buildType !in extendedTypeToOriginalTypeMapping.keys) {
            return
        }

        // Creates the configuration to handle this variant. Note that in the attributes
        // to match the configuration we use the original build type without `nonObfuscated`.
        val configuration = createConfigurationForVariant(
            variant = variant,
            originalBuildTypeName = extendedTypeToOriginalTypeMapping[variant.buildType] ?: "",
        )

        // Prepares a block to execute later that creates the tasks for this variant
        afterVariants {
            createTasksForVariant(
                project = project,
                variant = variant,
                configurationName = configuration.name,
                baselineProfileExtension = baselineProfileExtension
            )
        }
    }

    private fun createConfigurationForVariant(variant: Variant, originalBuildTypeName: String) =
        configurationManager.maybeCreate(
            nameParts = listOf(
                variant.flavorName ?: "",
                originalBuildTypeName,
                CONFIGURATION_NAME_BASELINE_PROFILES
            ),
            canBeConsumed = true,
            canBeResolved = false,
            buildType = originalBuildTypeName,
            productFlavors = variant.productFlavors
        )

    private fun createTasksForVariant(
        project: Project,
        variant: TestVariant,
        configurationName: String,
        baselineProfileExtension: BaselineProfileProducerExtension
    ) {

        // Prepares the devices list to use to generate the baseline profile.
        val devices = mutableSetOf<String>()
        devices.addAll(baselineProfileExtension.managedDevices)
        if (baselineProfileExtension.useConnectedDevices) devices.add("connected")

        // The test task runs the ui tests
        val testTasks = devices.map { device ->
            val task = InstrumentationTestTaskWrapper.getByName(
                project = project,
                device = device,
                variantName = variant.name
            )

            // The task is null if the managed device name does not exist
            if (task == null) {

                // If gradle is syncing don't throw any exception and simply stop here. This
                // plugin will fail at build time instead. This allows not breaking project
                // sync in ide.
                if (isGradleSyncRunning()) return

                throw GradleException(
                    """
                    It wasn't possible to determine the test task for managed device `$device`.
                    Please check the managed devices specified in the baseline profile
                    configuration.
                    """.trimIndent()
                )
            }

            task
        }.onEach { it.setEnableEmulatorDisplay(baselineProfileExtension.enableEmulatorDisplay) }

        // The collect task collects the baseline profile files from the ui test results
        val collectTaskProvider = CollectBaselineProfileTask.registerForVariant(
            project = project,
            variant = variant,
            testTaskDependencies = testTasks
        )

        // The artifacts are added to the configuration that exposes the generated baseline profile
        addArtifactToConfiguration(
            configurationName = configurationName,
            taskProvider = collectTaskProvider,
            artifactType = CONFIGURATION_ARTIFACT_TYPE
        )
    }
}
