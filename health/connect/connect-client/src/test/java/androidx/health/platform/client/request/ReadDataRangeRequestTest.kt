/*
 * Copyright (C) 2022 The Android Open Source Project
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
package androidx.health.platform.client.request

import android.os.Parcel
import androidx.health.platform.client.proto.DataProto
import androidx.health.platform.client.proto.RequestProto
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.junit.runner.RunWith

// Checks ipc serialization/deserialization
@RunWith(AndroidJUnit4::class)
class ReadDataRangeRequestTest {

    @Test
    fun writeToParcel() {
        val stepsProto = DataProto.DataType.newBuilder().setName("Steps").build()
        val readDataRangeRequest =
            ReadDataRangeRequest(
                RequestProto.ReadDataRangeRequest.newBuilder()
                    .setDataType(stepsProto)
                    .setLimit(100)
                    .setPageSize(50)
                    .setPageToken("pageToken")
                    .build()
            )

        val parcel: Parcel = Parcel.obtain()
        parcel.writeParcelable(readDataRangeRequest, 0)
        parcel.setDataPosition(0)
        @Suppress("Deprecation") // readParcelable deprecated in T and introduced new methods
        val out: ReadDataRangeRequest? =
            parcel.readParcelable(ReadDataRangeRequest::class.java.classLoader)
        assertThat(out?.proto).isEqualTo(readDataRangeRequest.proto)
    }
}
