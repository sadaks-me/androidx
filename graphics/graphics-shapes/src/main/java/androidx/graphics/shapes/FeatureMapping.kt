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

package androidx.graphics.shapes

import androidx.core.graphics.div
import androidx.core.graphics.minus
import androidx.core.graphics.plus

/**
 * MeasuredFeatures contains a list of all features in a polygon along with the [0..1] progress
 * at that feature
 */
internal typealias MeasuredFeatures = List<Pair<Float, Polygon.Feature>>

/**
 * featureMapper creates a mapping between the "features" (rounded corners) of two shapes
 */
internal fun featureMapper(features1: MeasuredFeatures, features2: MeasuredFeatures): DoubleMapper {
    // We only use corners for this mapping.
    val filteredFeatures1 = features1.filter { it.second is Polygon.Corner }
    val filteredFeatures2 = features2.filter { it.second is Polygon.Corner }

    val (m1, m2) = if (filteredFeatures1.size > filteredFeatures2.size) {
        doMapping(filteredFeatures2, filteredFeatures1) to filteredFeatures2
    } else {
        filteredFeatures1 to doMapping(filteredFeatures1, filteredFeatures2)
    }
    val mm = m1.zip(m2).map { (f1, f2) -> f1.first to f2.first }

    debugLog(LOG_TAG) { mm.joinToString { "${it.first} -> ${it.second}" } }
    return DoubleMapper(*mm.toTypedArray()).also { dm ->
        debugLog(LOG_TAG) {
            val N = 10
            "Map: " +
                (0..N).joinToString { i -> "%.3f".format(dm.map(i.toFloat() / N)) } +
            "\nMb : " +
                (0..N).joinToString { i -> "%.3f".format(dm.mapBack(i.toFloat() / N)) }
        }
    }
}

/**
 * Returns distance along overall shape between two Features on the two different shapes.
 * This information is used to determine how to map features (and the curves that make up
 * those features).
 */
internal fun featureDistSquared(f1: Polygon.Feature, f2: Polygon.Feature): Float {
    // TODO: We might want to enable concave-convex matching in some situations. If so, the
    //  approach below will not work
    if (f1 is Polygon.Corner && f2 is Polygon.Corner && f1.convex != f2.convex) {
        // Simple hack to force all features to map only to features of the same concavity, by
        // returning an infinitely large distance in that case
        debugLog(LOG_TAG) { "*** Feature distance ∞ for convex-vs-concave corners" }
        return Float.MAX_VALUE
    }
    val (c1, c2) = listOf(f1, f2).map { f ->
        (f.cubics.first().p0 + f.cubics.last().p3) / 2f
    }
    val d = c1 - c2
    return d.x * d.x + d.y * d.y
}

/**
 * Returns a mapping of the features in f2 that best map to the features in f1. The result
 * will be a list of features in f2 that is the size of f1. This is done to figure out
 * what the best features are in f2 that map to the existing features in f1. For example, if
 * f1 has 3 features and f2 has 4, we want to know what the 3 features are in f2 that map to
 * the features in f1 (then we will create a placeholder feature in the smaller shape for
 * the morph).
 */
internal fun doMapping(f1: MeasuredFeatures, f2: MeasuredFeatures): MeasuredFeatures {
    // Pick the first mapping in a greedy way.
    val ix = f2.indices.minBy { featureDistSquared(f1[0].second, f2[it].second) }

    val m = f1.size
    val n = f2.size

    val ret = mutableListOf(f2[ix])
    var lastPicked = ix
    for (i in 1 until m) {
        // Check the indices we can pick, which one is better.
        // Leave enough items in f2 to pick matches for the items left in f1.
        val last = (ix - (m - i)).let { if (it > lastPicked) it else it + n }
        val best = (lastPicked + 1..last).minBy {
            featureDistSquared(f1[i].second, f2[it % n].second)
        }
        ret.add(f2[best % n])
        lastPicked = best
    }
    return ret
}

private val LOG_TAG = "FeatureMapping"