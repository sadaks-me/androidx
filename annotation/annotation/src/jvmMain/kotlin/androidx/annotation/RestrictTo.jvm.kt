/*
 * Copyright (C) 2016 The Android Open Source Project
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

package androidx.annotation

import androidx.annotation.RestrictTo.Scope
import java.lang.annotation.ElementType

/**
 * Denotes that the annotated element should only be accessed from within a
 * specific scope (as defined by [Scope]).
 *
 *
 * Example of restricting usage within a library (based on gradle group ID):
 * ```
 * @RestrictTo(GROUP_ID)
 * public void resetPaddingToInitialValues() { ...
 * ```
 * Example of restricting usage to tests:
 * ```
 * @RestrictTo(Scope.TESTS)
 * public abstract int getUserId();
 * ```
 * Example of restricting usage to subclasses:
 * ```
 * @RestrictTo(Scope.SUBCLASSES)
 * public void onDrawForeground(Canvas canvas) { ...
 * ```
 */
@MustBeDocumented
@Retention(AnnotationRetention.BINARY)
@Target(
    AnnotationTarget.ANNOTATION_CLASS,
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER,
    AnnotationTarget.CONSTRUCTOR,
    AnnotationTarget.FIELD,
    AnnotationTarget.FILE,
)
// Needed due to Kotlin's lack of PACKAGE annotation target
// https://youtrack.jetbrains.com/issue/KT-45921
@Suppress("DEPRECATED_JAVA_ANNOTATION", "SupportAnnotationUsage")
@java.lang.annotation.Target(
    ElementType.ANNOTATION_TYPE,
    ElementType.TYPE,
    ElementType.METHOD,
    ElementType.CONSTRUCTOR,
    ElementType.FIELD,
    ElementType.PACKAGE,
)
public actual annotation class RestrictTo actual constructor(
    /**
     * The scope to which usage should be restricted.
     */
    actual vararg val value: Scope
) {
    public actual enum class Scope {
        /**
         * Restrict usage to code within the same library (e.g. the same
         * gradle group ID and artifact ID).
         */
        LIBRARY,

        /**
         * Restrict usage to code within the same group of libraries.
         * This corresponds to the gradle group ID.
         */
        LIBRARY_GROUP,

        /**
         * Restrict usage to code within packages whose groups share
         * the same library group prefix up to the last ".", so for
         * example libraries foo.bar:lib1 and foo.baz:lib2 share
         * the prefix "foo." and so they can use each other's
         * apis that are restricted to this scope. Similarly for
         * com.foo.bar:lib1 and com.foo.baz:lib2 where they share
         * "com.foo.". Library com.bar.qux:lib3 however will not
         * be able to use the restricted api because it only
         * shares the prefix "com." and not all the way until the
         * last ".".
         */
        LIBRARY_GROUP_PREFIX,

        /**
         * Restrict usage to code within the same group ID (based on gradle
         * group ID). This is an alias for [LIBRARY_GROUP_PREFIX].
         *
         * @deprecated Use [LIBRARY_GROUP_PREFIX] instead
         */
        @Deprecated("Use LIBRARY_GROUP_PREFIX instead.")
        GROUP_ID,

        /**
         * Restrict usage to tests.
         */
        TESTS,

        /**
         * Restrict usage to subclasses of the enclosing class.
         *
         * **Note:** This scope should not be used to annotate
         * packages.
         */
        SUBCLASSES,
    }
}
