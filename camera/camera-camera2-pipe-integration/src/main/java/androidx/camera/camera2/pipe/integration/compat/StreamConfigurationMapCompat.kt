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

package androidx.camera.camera2.pipe.integration.compat

import android.hardware.camera2.params.StreamConfigurationMap
import android.os.Build
import android.util.Size
import androidx.annotation.RequiresApi
import androidx.camera.camera2.pipe.integration.compat.workaround.OutputSizesCorrector
import androidx.camera.camera2.pipe.integration.config.CameraScope
import javax.inject.Inject

/**
 * Helper for accessing features in [StreamConfigurationMap] in a backwards compatible
 * fashion.
 *
 * @param map [StreamConfigurationMap] class to wrap workarounds when output sizes are retrieved.
 * @param outputSizesCorrector [OutputSizesCorrector] class to perform correction on sizes.
 */
@CameraScope
@RequiresApi(21)
class StreamConfigurationMapCompat @Inject constructor(
    map: StreamConfigurationMap,
    private val outputSizesCorrector: OutputSizesCorrector
) {
    private var impl: StreamConfigurationMapCompatImpl

    init {
        impl = if (Build.VERSION.SDK_INT >= 23) {
            StreamConfigurationMapCompatApi23Impl(map)
        } else {
            StreamConfigurationMapCompatBaseImpl(map)
        }
    }

    /**
     * Get a list of sizes compatible with the requested image `format`.
     *
     *
     * Output sizes related quirks will be applied onto the returned sizes list.
     *
     * @param format an image format from [ImageFormat] or [PixelFormat]
     * @return an array of supported sizes, or `null` if the `format` is not a
     * supported output
     */
    fun getOutputSizes(format: Int): Array<Size>? {
        return outputSizesCorrector.applyQuirks(impl.getOutputSizes(format), format)
    }

    /**
     * Get a list of sizes compatible with `klass` to use as an output.
     *
     *
     * Output sizes related quirks will be applied onto the returned sizes list.
     *
     * @param klass a non-`null` [Class] object reference
     * @return an array of supported sizes for [ImageFormat.PRIVATE] format,
     * or `null` if the `klass` is not a supported output.
     * @throws NullPointerException if `klass` was `null`
     */
    fun <T> getOutputSizes(klass: Class<T>): Array<Size>? {
        return outputSizesCorrector.applyQuirks<T>(impl.getOutputSizes<T>(klass), klass)
    }

    /**
     * Get a list of supported high resolution sizes, which cannot operate at full
     * [CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_BURST_CAPTURE] rate.
     *
     *
     * Output sizes related quirks will be applied onto the returned sizes list.
     *
     * @param format an image format from [ImageFormat] or [PixelFormat]
     * @return an array of supported sizes, or `null` if the `format` is not a
     * supported output
     */
    fun getHighResolutionOutputSizes(format: Int): Array<Size>? {
        return outputSizesCorrector.applyQuirks(impl.getHighResolutionOutputSizes(format), format)
    }

    /**
     * Returns the [StreamConfigurationMap] represented by this object.
     */
    fun toStreamConfigurationMap(): StreamConfigurationMap {
        return impl.unwrap()
    }

    internal interface StreamConfigurationMapCompatImpl {
        fun getOutputSizes(format: Int): Array<Size>?
        fun <T> getOutputSizes(klass: Class<T>): Array<Size>?
        fun getHighResolutionOutputSizes(format: Int): Array<Size>?
        /**
         * Returns the underlying [StreamConfigurationMap] instance.
         */
        fun unwrap(): StreamConfigurationMap
    }
}