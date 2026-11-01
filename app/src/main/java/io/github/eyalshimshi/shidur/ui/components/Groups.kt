package io.github.eyalshimshi.shidur.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Grouped lists: items share one card with large outer corners and small corners between them. */
object GroupShapes {
    val Outer: Dp = 24.dp
    val Inner: Dp = 4.dp
    val Gap: Dp = 2.dp
}

/** The shape of item [index] in a group of [count] items. */
fun groupItemShape(index: Int, count: Int): Shape {
    val first = index == 0
    val last = index == count - 1
    return RoundedCornerShape(
        topStart = if (first) GroupShapes.Outer else GroupShapes.Inner,
        topEnd = if (first) GroupShapes.Outer else GroupShapes.Inner,
        bottomEnd = if (last) GroupShapes.Outer else GroupShapes.Inner,
        bottomStart = if (last) GroupShapes.Outer else GroupShapes.Inner,
    )
}

/** Draws a list item as part of its group's card. */
fun Modifier.groupItem(index: Int, count: Int, color: Color): Modifier =
    padding(horizontal = 16.dp)
        .clip(groupItemShape(index, count))
        .background(color)

/**
 * Space that lists leave at their end so their last items can scroll above the mini player and
 * the navigation bar, which are drawn over the content.
 */
val LocalBottomContentPadding = staticCompositionLocalOf { 0.dp }
