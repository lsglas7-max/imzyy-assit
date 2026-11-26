package id.imzyy.xl

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/** IMZYY monochrome palette: black / white only. */
object Ink {
    val Black = Color(0xFF000000)
    val Surface = Color(0xFF0A0A0A)
    val Raised = Color(0xFF141414)
    val Line = Color(0xFF262626)
    val Text = Color(0xFFF5F5F5)
    val Sub = Color(0xFF8C8C8C)
    val White = Color(0xFFFFFFFF)
}

@Composable
fun Pressable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val pressed by src.collectIsPressedAsState()
    val s by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 500f),
        label = "press",
    )
    Box(
        modifier = modifier
            .scale(s)
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(interactionSource = src, indication = null, enabled = enabled, onClick = onClick),
        content = content,
    )
}

@Composable
fun Panel(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Ink.Surface)
            .border(1.dp, Ink.Line, shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
fun InkButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, filled: Boolean = true, enabled: Boolean = true) {
    val shape = RoundedCornerShape(14.dp)
    Pressable(onClick = onClick, modifier = modifier, enabled = enabled) {
        Box(
            Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(if (filled) Ink.White else Ink.Raised)
                .border(1.dp, if (filled) Ink.White else Ink.Line, shape)
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text,
                color = if (filled) Ink.Black else Ink.Text,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                letterSpacing = 0.6.sp,
            )
        }
    }
}

@Composable
fun Chip(text: String, selected: Boolean, compact: Boolean = false, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) Ink.White else Ink.Raised, tween(220), label = "chipbg")
    val fg by animateColorAsState(if (selected) Ink.Black else Ink.Sub, tween(220), label = "chipfg")
    val shape = RoundedCornerShape(50)
    Pressable(onClick = onClick) {
        Box(
            Modifier
                .clip(shape)
                .background(bg)
                .border(1.dp, Ink.Line, shape)
                .padding(horizontal = if (compact) 10.dp else 14.dp, vertical = 8.dp),
        ) {
            Text(text, color = fg, fontSize = if (compact) 11.sp else 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun Reveal(index: Int, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * 60L)
        shown = true
    }
    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(tween(450)) + slideInVertically(tween(450)) { it / 8 },
    ) { content() }
}

@Composable
fun InkProgress(fraction: Float) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), tween(250), label = "progress")
    Box(
        Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Ink.Line),
    ) {
        Box(Modifier.fillMaxWidth(f).fillMaxHeight().background(Ink.White))
    }
}

@Composable
fun InkField(value: String, onChange: (String) -> Unit, hint: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Ink.Raised)
            .border(1.dp, Ink.Line, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        if (value.isEmpty()) Text(hint, color = Ink.Sub, fontSize = 14.sp)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = Ink.Text, fontSize = 14.sp, fontFamily = FontFamily.SansSerif),
            cursorBrush = SolidColor(Ink.White),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Slow drifting grid + a soft scan band. Pure Canvas, no bitmaps, very cheap. */
@Composable
fun AnimatedBackdrop(modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "backdrop")
    val p by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing)),
        label = "backdropP",
    )
    Canvas(modifier.fillMaxSize()) {
        val step = 56.dp.toPx()
        val shift = p * step
        var x = -step + shift
        while (x < size.width + step) {
            drawLine(Color(0x12FFFFFF), Offset(x, 0f), Offset(x - size.height * 0.25f, size.height), 1f)
            x += step
        }
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color(0x16FFFFFF), Color.Transparent),
                center = Offset(size.width * (0.2f + 0.6f * p), size.height * 0.18f),
                radius = size.width * 0.85f,
            ),
        )
        for (i in 0 until 26) {
            val px = ((i * 9301f + 49297f) % 233280f) / 233280f
            val speed = 0.4f + (i % 5) * 0.15f
            val py = 1f - ((p * speed + i * 0.037f) % 1f)
            drawCircle(
                color = Color(0x33FFFFFF),
                radius = (1f + (i % 3)) * density,
                center = Offset(px * size.width, py * size.height),
            )
        }
        val band = 160.dp.toPx()
        val cy = (p * (size.height + band * 2)) - band
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color(0x0FFFFFFF), Color.Transparent),
                startY = cy - band,
                endY = cy + band,
            ),
            topLeft = Offset(0f, cy - band),
            size = Size(size.width, band * 2),
        )
    }
}

@Composable
fun InkSlider(value: Float, min: Float, max: Float, onChange: (Float) -> Unit) {
    var w by remember { mutableStateOf(1f) }
    val frac = ((value - min) / (max - min)).coerceIn(0f, 1f)
    fun at(x: Float) = min + (max - min) * (x / w).coerceIn(0f, 1f)
    Box(
        Modifier
            .fillMaxWidth()
            .height(34.dp)
            .onSizeChanged { w = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(min, max) { detectTapGestures { off -> onChange(at(off.x)) } }
            .pointerInput(min, max) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    onChange(at(change.position.x))
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)).background(Ink.Line))
        Box(Modifier.fillMaxWidth(frac).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Ink.White))
        Box(
            Modifier
                .offset { IntOffset((frac * w).toInt() - 10.dp.roundToPx(), 0) }
                .size(20.dp)
                .clip(CircleShape)
                .background(Ink.White),
        )
    }
}

@Composable
fun ShimmerTitle(text: String, sizeSp: Int = 36) {
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(
        initialValue = -400f,
        targetValue = 1100f,
        animationSpec = infiniteRepeatable(tween(3400, easing = LinearEasing)),
        label = "shimmerX",
    )
    val brush = Brush.linearGradient(
        colors = listOf(Color(0xFF5E5E5E), Color(0xFFFFFFFF), Color(0xFF5E5E5E)),
        start = Offset(x, 0f),
        end = Offset(x + 360f, 0f),
    )
    Text(
        text,
        style = TextStyle(brush = brush, fontSize = sizeSp.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp),
    )
}

@Composable
fun GlowButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "glow")
    val a by t.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.42f,
        animationSpec = infiniteRepeatable(animation = tween(1800, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "glowA",
    )
    Pressable(onClick = onClick, modifier = modifier) {
        Box(
            Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawRoundRect(
                        color = Color.White.copy(alpha = a),
                        topLeft = Offset(-6f, -6f),
                        size = Size(size.width + 12f, size.height + 12f),
                        cornerRadius = CornerRadius(22.dp.toPx(), 22.dp.toPx()),
                    )
                }
                .clip(RoundedCornerShape(16.dp))
                .background(Ink.White)
                .padding(vertical = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, color = Ink.Black, fontWeight = FontWeight.Black, fontSize = 17.sp, letterSpacing = 3.sp)
        }
    }
}
