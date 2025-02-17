/*
 * Copyright 2020 The Android Open Source Project
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

package androidx.compose.foundation.text

import androidx.compose.foundation.text.selection.LocalSelectionRegistrar
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.SelectionRegistrar
import androidx.compose.foundation.text.selection.hasSelection
import androidx.compose.runtime.Composable
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow

/**
 * Basic element that displays text and provides semantics / accessibility information.
 * Typically you will instead want to use [androidx.compose.material.Text], which is
 * a higher level Text element that contains semantics and consumes style information from a theme.
 *
 * @param text The text to be displayed.
 * @param modifier [Modifier] to apply to this layout node.
 * @param style Style configuration for the text such as color, font, line height etc.
 * @param onTextLayout Callback that is executed when a new text layout is calculated. A
 * [TextLayoutResult] object that callback provides contains paragraph information, size of the
 * text, baselines and other details. The callback can be used to add additional decoration or
 * functionality to the text. For example, to draw selection around the text.
 * @param overflow How visual overflow should be handled.
 * @param softWrap Whether the text should break at soft line breaks. If false, the glyphs in the
 * text will be positioned as if there was unlimited horizontal space. If [softWrap] is false,
 * [overflow] and TextAlign may have unexpected effects.
 * @param maxLines An optional maximum number of lines for the text to span, wrapping if
 * necessary. If the text exceeds the given number of lines, it will be truncated according to
 * [overflow] and [softWrap]. It is required that 1 <= [minLines] <= [maxLines].
 * @param minLines The minimum height in terms of minimum number of visible lines. It is required
 * that 1 <= [minLines] <= [maxLines].
 */
@OptIn(InternalFoundationTextApi::class)
@Composable
fun BasicText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1
) {
    @Suppress("DEPRECATION")
    if (NewTextRendering1_5) {
        TextUsingModifier(
            text = text,
            modifier = modifier,
            style = style,
            onTextLayout = onTextLayout,
            overflow = overflow,
            softWrap = softWrap,
            maxLines = maxLines,
            minLines = minLines
        )
        return
    }
    // NOTE(text-perf-review): consider precomputing layout here by pushing text to a channel...
    // something like:
    // remember(text) { precomputeTextLayout(text) }

    // Unlike text field for which validation happens inside the 'heightInLines' modifier, in text
    // 'maxLines' are not handled by the modifier but instead passed to the StaticLayout, therefore
    // we perform validation here
    validateMinMaxLines(minLines, maxLines)

    // selection registrar, if no SelectionContainer is added ambient value will be null
    val selectionRegistrar = LocalSelectionRegistrar.current
    val density = LocalDensity.current
    val fontFamilyResolver = LocalFontFamilyResolver.current

    // The ID used to identify this CoreText. If this CoreText is removed from the composition
    // tree and then added back, this ID should stay the same.
    // Notice that we need to update selectable ID when the input text or selectionRegistrar has
    // been updated.
    // When text is updated, the selection on this CoreText becomes invalid. It can be treated
    // as a brand new CoreText.
    // When SelectionRegistrar is updated, CoreText have to request a new ID to avoid ID collision.

    // NOTE(text-perf-review): potential bug. selectableId is regenerated here whenever text
    // changes, but it is only saved in the initial creation of TextState.
    val selectableId = if (selectionRegistrar == null) {
        SelectionRegistrar.InvalidSelectableId
    } else {
        rememberSaveable(text, selectionRegistrar, saver = selectionIdSaver(selectionRegistrar)) {
            selectionRegistrar.nextSelectableId()
        }
    }

    val controller = remember {
        TextController(
            TextState(
                TextDelegate(
                    text = AnnotatedString(text),
                    style = style,
                    density = density,
                    softWrap = softWrap,
                    fontFamilyResolver = fontFamilyResolver,
                    overflow = overflow,
                    maxLines = maxLines,
                    minLines = minLines,
                ),
                selectableId
            )
        )
    }
    val state = controller.state
    if (!currentComposer.inserting) {
        controller.setTextDelegate(
            updateTextDelegate(
                current = state.textDelegate,
                text = text,
                style = style,
                density = density,
                softWrap = softWrap,
                fontFamilyResolver = fontFamilyResolver,
                overflow = overflow,
                maxLines = maxLines,
                minLines = minLines,
            )
        )
    }
    state.onTextLayout = onTextLayout ?: {}
    controller.update(selectionRegistrar)
    if (selectionRegistrar != null) {
        state.selectionBackgroundColor = LocalTextSelectionColors.current.backgroundColor
    }

    Layout(
        modifier = modifier
            .textPointerHoverIcon(selectionRegistrar)
            .then(controller.modifiers),
        measurePolicy = controller.measurePolicy
    )
}

/**
 * Basic element that displays text and provides semantics / accessibility information.
 * Typically you will instead want to use [androidx.compose.material.Text], which is
 * a higher level Text element that contains semantics and consumes style information from a theme.
 *
 * @param text The text to be displayed.
 * @param modifier [Modifier] to apply to this layout node.
 * @param style Style configuration for the text such as color, font, line height etc.
 * @param onTextLayout Callback that is executed when a new text layout is calculated. A
 * [TextLayoutResult] object that callback provides contains paragraph information, size of the
 * text, baselines and other details. The callback can be used to add additional decoration or
 * functionality to the text. For example, to draw selection around the text.
 * @param overflow How visual overflow should be handled.
 * @param softWrap Whether the text should break at soft line breaks. If false, the glyphs in the
 * text will be positioned as if there was unlimited horizontal space. If [softWrap] is false,
 * [overflow] and TextAlign may have unexpected effects.
 * @param maxLines An optional maximum number of lines for the text to span, wrapping if
 * necessary. If the text exceeds the given number of lines, it will be truncated according to
 * [overflow] and [softWrap]. It is required that 1 <= [minLines] <= [maxLines].
 * @param minLines The minimum height in terms of minimum number of visible lines. It is required
 * that 1 <= [minLines] <= [maxLines].
 * @param inlineContent A map store composables that replaces certain ranges of the text. It's
 * used to insert composables into text layout. Check [InlineTextContent] for more information.
 */
@OptIn(InternalFoundationTextApi::class)
@Composable
fun BasicText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    inlineContent: Map<String, InlineTextContent> = mapOf()
) {
    @Suppress("DEPRECATION")
    if (NewTextRendering1_5) {
        TextUsingModifier(
            text = text,
            modifier = modifier,
            style = style,
            onTextLayout = onTextLayout,
            overflow = overflow,
            softWrap = softWrap,
            maxLines = maxLines,
            minLines = minLines,
            inlineContent = inlineContent
        )
        return
    }
    // Unlike text field for which validation happens inside the 'heightInLines' modifier, in text
    // 'maxLines' are not handled by the modifier but instead passed to the StaticLayout, therefore
    // we perform validation here
    validateMinMaxLines(minLines, maxLines)

    // selection registrar, if no SelectionContainer is added ambient value will be null
    val selectionRegistrar = LocalSelectionRegistrar.current
    val density = LocalDensity.current
    val fontFamilyResolver = LocalFontFamilyResolver.current
    val selectionBackgroundColor = LocalTextSelectionColors.current.backgroundColor

    val (placeholders, inlineComposables) = resolveInlineContent(text, inlineContent)

    // The ID used to identify this CoreText. If this CoreText is removed from the composition
    // tree and then added back, this ID should stay the same.
    // Notice that we need to update selectable ID when the input text or selectionRegistrar has
    // been updated.
    // When text is updated, the selection on this CoreText becomes invalid. It can be treated
    // as a brand new CoreText.
    // When SelectionRegistrar is updated, CoreText have to request a new ID to avoid ID collision.

    // NOTE(text-perf-review): potential bug. selectableId is regenerated here whenever text
    // changes, but it is only saved in the initial creation of TextState.
    val selectableId = if (selectionRegistrar == null) {
        SelectionRegistrar.InvalidSelectableId
    } else {
        rememberSaveable(text, selectionRegistrar, saver = selectionIdSaver(selectionRegistrar)) {
            selectionRegistrar.nextSelectableId()
        }
    }

    val controller = remember {
        TextController(
            TextState(
                TextDelegate(
                    text = text,
                    style = style,
                    density = density,
                    softWrap = softWrap,
                    fontFamilyResolver = fontFamilyResolver,
                    overflow = overflow,
                    minLines = minLines,
                    maxLines = maxLines,
                    placeholders = placeholders
                ),
                selectableId
            )
        )
    }
    val state = controller.state
    if (!currentComposer.inserting) {
        controller.setTextDelegate(
            updateTextDelegate(
                current = state.textDelegate,
                text = text,
                style = style,
                density = density,
                softWrap = softWrap,
                fontFamilyResolver = fontFamilyResolver,
                overflow = overflow,
                maxLines = maxLines,
                minLines = minLines,
                placeholders = placeholders,
            )
        )
    }
    state.onTextLayout = onTextLayout ?: {}
    state.selectionBackgroundColor = selectionBackgroundColor

    controller.update(selectionRegistrar)

    Layout(
        content = if (inlineComposables.isEmpty()) {
            {}
        } else {
            { InlineChildren(text, inlineComposables) }
        },
        modifier = modifier
            .textPointerHoverIcon(selectionRegistrar)
            .then(controller.modifiers),
        measurePolicy = controller.measurePolicy
    )
}

@Deprecated("Maintained for binary compatibility", level = DeprecationLevel.HIDDEN)
@Composable
fun BasicText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style,
        onTextLayout = onTextLayout,
        overflow = overflow,
        softWrap = softWrap,
        minLines = 1,
        maxLines = maxLines
    )
}

@Deprecated("Maintained for binary compatibility", level = DeprecationLevel.HIDDEN)
@Composable
fun BasicText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    inlineContent: Map<String, InlineTextContent> = mapOf(),
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style,
        onTextLayout = onTextLayout,
        overflow = overflow,
        softWrap = softWrap,
        minLines = 1,
        maxLines = maxLines,
        inlineContent = inlineContent
    )
}

/**
 * Optionally use legacy text rendering stack from 1.4.
 *
 * This flag will be removed by 1.5 beta01. If you find any issues with the new stack, flip this
 * flag to false to confirm they are newly introduced then file a bug.
 */
@Deprecated(
    message = "This flag will be removed by 1.5 beta1 and should only be used for debugging " +
        "text related issues in the new 1.5 text stack.",
    replaceWith = ReplaceWith(""),
    level = DeprecationLevel.WARNING
)
var NewTextRendering1_5: Boolean by mutableStateOf(false)

/**
 * A custom saver that won't save if no selection is active.
 */
private fun selectionIdSaver(selectionRegistrar: SelectionRegistrar?) = Saver<Long, Long>(
    save = { if (selectionRegistrar.hasSelection(it)) it else null },
    restore = { it }
)

internal expect fun Modifier.textPointerHoverIcon(selectionRegistrar: SelectionRegistrar?): Modifier