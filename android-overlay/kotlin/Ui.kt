package id.imzyy.xl

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
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
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(if (selected) Ink.White else Ink.Raised, tween(220), label = "chipbg")
    val fg by animateColorAsState(if (selected) Ink.Black else Ink.Sub, tween(220), label = "chipfg")
    val shape = RoundedCornerShape(50)
    Pressable(onClick = onClick) {
        Box(
            Modifier
                .clip(shape)
                .background(bg)
                .border(1.dp, Ink.Line, shape)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text(text, color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
