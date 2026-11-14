package com.edubuddy.app.ui.common

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.edubuddy.app.ui.theme.EduTheme

/** Standard horizontal page gutter. */
val Gutter = 20.dp

// ---- Brand ---------------------------------------------------------------------------------

/**
 * The EduBuddy mark drawn natively (crisp at any size, no bitmap): a day bracket holding the
 * ember "now" dot. Geometry matches res/drawable/ic_launcher_foreground.xml.
 */
@Composable
fun BrandMark(
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    color: Color = MaterialTheme.colorScheme.onSurface,
    dot: Color = EduTheme.extra.accent,
) {
    Canvas(modifier.size(size).semantics { contentDescription = "EduBuddy" }) { drawMark(color, dot) }
}

private fun DrawScope.drawMark(color: Color, dot: Color) {
    // Source geometry lives in a 34 x 42 box (x 36..70, y 33..75 of the 108 icon grid).
    val s = size.height / 42f
    val ox = (size.width - 34f * s) / 2f
    fun x(v: Float) = ox + (v - 36f) * s
    fun y(v: Float) = (v - 33f) * s
    val r = 5f * s
    // Bracket built from three rounded bars; same fill, so overlaps are invisible.
    drawRoundRect(color, Offset(x(36f), y(33f)), Size(10f * s, 42f * s), CornerRadius(r))
    drawRoundRect(color, Offset(x(36f), y(33f)), Size(34f * s, 10f * s), CornerRadius(r))
    drawRoundRect(color, Offset(x(36f), y(65f)), Size(34f * s, 10f * s), CornerRadius(r))
    drawCircle(dot, radius = 6f * s, center = Offset(x(63f), y(54f)))
}

/** The app icon as a tile, for the lock screen and profile card. */
@Composable
fun AppLogo(size: Dp = 72.dp, modifier: Modifier = Modifier) {
    val ex = EduTheme.extra
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.28f)).background(com.edubuddy.app.ui.theme.Brand.Ink),
        contentAlignment = Alignment.Center,
    ) {
        BrandMark(size = size * 0.46f, color = com.edubuddy.app.ui.theme.Brand.Paper, dot = ex.accent)
    }
}

/** Mark + name, used where the brand needs to be named (welcome, about). */
@Composable
fun Wordmark(modifier: Modifier = Modifier, markSize: Dp = 22.dp) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        BrandMark(size = markSize)
        Spacer(Modifier.width(markSize * 0.45f))
        Text("EduBuddy", style = MaterialTheme.typography.titleLarge)
    }
}

// ---- Interaction ---------------------------------------------------------------------------

/** A gentle press-down scale. Pair with the same [interactionSource] the clickable uses. */
fun Modifier.pressScale(interactionSource: InteractionSource, pressed: Float = 0.975f): Modifier = composed {
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (isPressed) pressed else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    graphicsLayer { scaleX = scale; scaleY = scale }
}

/** Short haptic ticks for meaningful state changes (marking attendance, completing a task). */
class Haptics(private val view: android.view.View) {
    fun confirm() {
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
        )
    }
    fun tick() {
        view.performHapticFeedback(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) HapticFeedbackConstants.KEYBOARD_TAP else HapticFeedbackConstants.VIRTUAL_KEY
        )
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    return remember(view) { Haptics(view) }
}

// ---- Layout --------------------------------------------------------------------------------

/** Large page title used at the top of each tab. */
@Composable
fun PageHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().padding(start = Gutter, end = 8.dp, top = 16.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f)) {
            if (subtitle != null) {
                Text(subtitle.uppercase(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(2.dp))
            }
            Text(title, style = MaterialTheme.typography.headlineLarge)
        }
        Row(verticalAlignment = Alignment.CenterVertically, content = actions)
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(start = Gutter, end = 8.dp, top = 28.dp, bottom = 8.dp).height(32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 12.dp)) {
                Text(actionLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** The app's standard card: white on paper, hairline edge, presses down slightly when tappable. */
@Composable
fun EduCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = containerColor)
    val border = BorderStroke(1.dp, EduTheme.extra.hairline)
    val shape = MaterialTheme.shapes.large
    if (onClick != null) {
        val source = remember { MutableInteractionSource() }
        Card(onClick = onClick, modifier = modifier.pressScale(source), shape = shape, colors = colors, border = border, interactionSource = source) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    } else {
        Card(modifier = modifier, shape = shape, colors = colors, border = border) {
            Column(Modifier.padding(contentPadding), content = content)
        }
    }
}

/** The single high-emphasis surface on a screen: solid ink, no decoration. */
@Composable
fun HeroCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.clip(MaterialTheme.shapes.extraLarge).background(EduTheme.extra.hero).padding(20.dp),
        content = content,
    )
}

/** Small icon on a neutral tile. Colour goes to the glyph only, so lists stay calm. */
@Composable
fun IconBadge(
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier,
    size: Dp = 40.dp,
    iconSize: Dp = 20.dp,
) {
    Box(
        modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(MaterialTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(iconSize))
    }
}

@Composable
fun ColorDot(color: Color, size: Dp = 10.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).clip(CircleShape).background(color))
}

/** Small label. Outlined by default; [filled] for a strong state such as "Now". */
@Composable
fun Pill(
    text: String,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    filled: Boolean = false,
) {
    val shape = RoundedCornerShape(6.dp)
    Row(
        modifier
            .clip(shape)
            .then(if (filled) Modifier.background(color) else Modifier.background(color.copy(alpha = 0.10f)))
            .padding(horizontal = 7.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = if (filled) Color.White else color
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(text, style = MaterialTheme.typography.labelMedium, color = fg, maxLines = 1)
    }
}

/** A number with a label. Neutral by design; [accent] only colours the caption (e.g. "2 overdue"). */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accent: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    caption: String? = null,
    onClick: (() -> Unit)? = null,
) {
    EduCard(modifier = modifier, onClick = onClick, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(6.dp))
            }
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(10.dp))
        Text(value, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            caption ?: " ",
            style = MaterialTheme.typography.labelMedium,
            color = if (caption != null) accent else Color.Transparent,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Animated circular progress with centred content. [progress] in 0..1. */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    stroke: Dp = 5.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    content: @Composable () -> Unit = {},
) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(700), label = "ring")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val s = Stroke(width = stroke.toPx(), cap = StrokeCap.Round)
            val inset = stroke.toPx() / 2
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            drawArc(trackColor, 0f, 360f, false, topLeft, arcSize, style = s)
            if (animated > 0f) drawArc(color, -90f, 360f * animated, false, topLeft, arcSize, style = s)
        }
        content()
    }
}

/** Thin progress bar. Drawn directly instead of via LinearProgressIndicator: cheaper, no stop dot. */
@Composable
fun ThinProgress(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
    trackColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
) {
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(700), label = "bar")
    Canvas(modifier.fillMaxWidth().height(height)) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(trackColor, cornerRadius = r)
        if (animated > 0f) drawRoundRect(color, size = Size((size.width * animated).coerceAtLeast(size.height), size.height), cornerRadius = r)
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(64.dp).clip(CircleShape).border(1.dp, EduTheme.extra.hairline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(26.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(
            message, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            OutlinedButton(onClick = onAction, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) { Text(actionLabel) }
        }
    }
}

/** Compact inline empty row inside a section. */
@Composable
fun InlineEmpty(text: String, modifier: Modifier = Modifier, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = Gutter)
            .clip(MaterialTheme.shapes.large)
            .border(1.dp, EduTheme.extra.hairline, MaterialTheme.shapes.large)
            .padding(start = 16.dp, end = 4.dp)
            .height(52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        if (actionLabel != null && onAction != null) TextButton(onClick = onAction) { Text(actionLabel) }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = false,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(); onDismiss() },
                colors = if (destructive) ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error) else ButtonDefaults.textButtonColors(),
            ) { Text(confirmLabel, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    )
}

/** A tappable settings-style row: plain glyph, title, optional subtitle and trailing content. */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = Gutter, vertical = 14.dp)
            .semantics(mergeDescendants = true) { contentDescription = listOfNotNull(title, subtitle).joinToString(", ") },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(22.dp), tint = iconTint)
            Spacer(Modifier.width(18.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
fun GroupLabel(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = modifier.padding(start = Gutter, end = Gutter, top = 28.dp, bottom = 6.dp),
    )
}

/** Hairline divider inset to the content edge. */
@Composable
fun Hairline(modifier: Modifier = Modifier, start: Dp = 0.dp) {
    HorizontalDivider(modifier.padding(start = start), thickness = 1.dp, color = EduTheme.extra.hairline)
}

@Composable
fun TonalSurface(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(modifier, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, content = content)
}

/**
 * Primary floating action: an ink capsule. Built on the non-animated overload because the
 * animated `text =` overload can drop its label from the accessibility tree when a tab's state
 * is restored, leaving TalkBack users with an unlabeled "Button".
 */
@Composable
fun EduFab(text: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val source = remember { MutableInteractionSource() }
    androidx.compose.material3.ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = modifier.pressScale(source, 0.95f),
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        elevation = androidx.compose.material3.FloatingActionButtonDefaults.elevation(2.dp, 4.dp, 2.dp, 3.dp),
        interactionSource = source,
    ) {
        Icon(icon, contentDescription = null, Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun SpacedRow(modifier: Modifier = Modifier, spacing: Dp = 12.dp, content: @Composable RowScope.() -> Unit) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(spacing), content = content)
}

/**
 * Text tabs with an ink underline that slides between options. Quieter than a filled segmented
 * control and reads as navigation rather than a form input.
 */
@Composable
fun <T> UnderlineTabs(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val index = options.indexOf(selected).coerceAtLeast(0)
    androidx.compose.material3.TabRow(
        selectedTabIndex = index,
        modifier = modifier.fillMaxWidth().padding(horizontal = Gutter),
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        indicator = { positions ->
            if (index < positions.size) {
                androidx.compose.material3.TabRowDefaults.PrimaryIndicator(
                    Modifier.tabIndicatorOffsetCompat(positions[index]),
                    width = 28.dp, height = 2.dp, color = MaterialTheme.colorScheme.onSurface,
                    shape = RoundedCornerShape(1.dp),
                )
            }
        },
        divider = { Hairline() },
    ) {
        options.forEachIndexed { i, opt ->
            androidx.compose.material3.Tab(
                selected = i == index,
                onClick = { onSelect(opt) },
                selectedContentColor = MaterialTheme.colorScheme.onSurface,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                text = { Text(label(opt), style = MaterialTheme.typography.titleSmall) },
            )
        }
    }
}

private fun Modifier.tabIndicatorOffsetCompat(position: androidx.compose.material3.TabPosition): Modifier =
    with(androidx.compose.material3.TabRowDefaults) { this@tabIndicatorOffsetCompat.tabIndicatorOffset(position) }
