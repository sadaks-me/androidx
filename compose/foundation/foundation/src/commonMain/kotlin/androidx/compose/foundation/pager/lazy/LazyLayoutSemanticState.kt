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

package androidx.compose.foundation.pager.lazy

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.layout.LazyLayoutSemanticState
import androidx.compose.ui.semantics.CollectionInfo

@OptIn(ExperimentalFoundationApi::class)
internal fun LazyLayoutSemanticState(
    state: PagerState,
    isVertical: Boolean
): LazyLayoutSemanticState = object : LazyLayoutSemanticState {

    override val currentPosition: Float
        get() = state.firstVisiblePage + state.firstVisiblePageOffset / 100_000f
    override val canScrollForward: Boolean
        get() = state.canScrollForward

    override suspend fun animateScrollBy(delta: Float) {
        state.animateScrollBy(delta)
    }

    override suspend fun scrollToItem(index: Int) {
        state.scrollToPage(index)
    }

    override fun collectionInfo(): CollectionInfo =
        if (isVertical) {
            CollectionInfo(rowCount = -1, columnCount = 1)
        } else {
            CollectionInfo(rowCount = 1, columnCount = -1)
        }
}