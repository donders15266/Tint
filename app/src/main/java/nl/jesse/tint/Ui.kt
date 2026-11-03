package nl.jesse.tint

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Donkere kleuren met veel contrast, zoals in de Clock-app. */
object Ui {
    val Bg = Color(0xFF000000)
    val Cell = Color(0xFF1C1C1E)
    val Fill = Color(0xFF2C2C2E)
    val Separator = Color(0xFF3A3A3C)
    val Label = Color(0xFFFFFFFF)
    val Secondary = Color(0xFFAEAEB2)
    val Tertiary = Color(0xFF8E8E93)
    val Green = Color(0xFF30D158)
    val Red = Color(0xFFFF453A)
}

// ---------- Pictogrammen ----------

enum class Glyph { IMAGE, SPARK, LINE, HOME, INFO, CHEVRON, CHECK }

/** Eenvoudige lijnpictogrammen, getekend zodat er geen icoonbibliotheek nodig is. */
@Composable
fun Icon(icon: Glyph, modifier: Modifier = Modifier, color: Color = Ui.Label, size: Dp = 24.dp, weight: Float = 1.9f) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        val w = weight.dp.toPx()
        val stroke = Stroke(w, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(color, Offset(x1 * s, y1 * s), Offset(x2 * s, y2 * s), w, StrokeCap.Round)
        fun poly(vararg p: Float) {
            val path = Path().apply {
                moveTo(p[0] * s, p[1] * s)
                for (i in 2 until p.size step 2) lineTo(p[i] * s, p[i + 1] * s)
            }
            drawPath(path, color, style = stroke)
        }
        when (icon) {
            Glyph.IMAGE -> {
                drawRoundRect(color, Offset(s * 0.1f, s * 0.16f), Size(s * 0.8f, s * 0.68f), CornerRadius(s * 0.12f), style = stroke)
                drawCircle(color, s * 0.07f, Offset(s * 0.34f, s * 0.38f))
                poly(0.14f, 0.76f, 0.4f, 0.54f, 0.58f, 0.68f, 0.7f, 0.58f, 0.88f, 0.74f)
            }
            Glyph.SPARK -> {
                for (r in 0 until 3) for (c in 0 until 3) {
                    val lit = r == 1 || c == 1
                    drawCircle(color.copy(alpha = if (lit) 1f else 0.4f), s * (if (r == 1 && c == 1) 0.11f else 0.07f), Offset(s * (0.2f + c * 0.3f), s * (0.2f + r * 0.3f)))
                }
            }
            Glyph.LINE -> {
                drawRoundRect(color, Offset(s * 0.22f, s * 0.08f), Size(s * 0.56f, s * 0.84f), CornerRadius(s * 0.1f), style = stroke)
                drawLine(color, Offset(s * 0.3f, s * 0.2f), Offset(s * 0.56f, s * 0.2f), w * 1.4f, StrokeCap.Round)
            }
            Glyph.HOME -> {
                poly(0.12f, 0.5f, 0.5f, 0.14f, 0.88f, 0.5f)
                poly(0.22f, 0.42f, 0.22f, 0.86f, 0.78f, 0.86f, 0.78f, 0.42f)
                line(0.44f, 0.86f, 0.44f, 0.64f); line(0.44f, 0.64f, 0.56f, 0.64f); line(0.56f, 0.64f, 0.56f, 0.86f)
            }
            Glyph.INFO -> {
                drawCircle(color, s * 0.42f, Offset(s / 2, s / 2), style = stroke)
                line(0.5f, 0.46f, 0.5f, 0.72f)
                drawCircle(color, w * 0.9f, Offset(s / 2, s * 0.3f))
            }
            Glyph.CHEVRON -> poly(0.36f, 0.18f, 0.66f, 0.5f, 0.36f, 0.82f)
            Glyph.CHECK -> poly(0.18f, 0.52f, 0.4f, 0.74f, 0.84f, 0.26f)
        }
    }
}

// ---------- Glas ----------

fun Modifier.glass(shape: androidx.compose.ui.graphics.Shape = CircleShape, tint: Color = Color(0xFF1C1C1E)) = this
    .clip(shape)
    .background(tint)
    .border(1.dp, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.28f), Color.White.copy(alpha = 0.06f))), shape)

/** Zwevende tabbalk onderaan in glas. */
@Composable
fun TabBar(selected: Tab, accent: Color, onSelect: (Tab) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .padding(horizontal = 14.dp)
            .fillMaxWidth()
            .height(66.dp)
            .glass(RoundedCornerShape(50))
            .padding(4.dp),
    ) {
        Tab.entries.forEach { tab ->
            val active = tab == selected
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(50))
                    .background(if (active) Color.White.copy(alpha = 0.12f) else Color.Transparent)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onSelect(tab) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                val color = if (active) accent else Ui.Label
                Icon(tab.icon, color = color, size = 23.dp)
                Spacer(Modifier.height(3.dp))
                Text(tab.label, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}

// ---------- Lijsten ----------

@Composable
fun LargeTitle(title: String, subtitle: String? = null) {
    Column(Modifier.statusBarsPadding().padding(horizontal = 20.dp).padding(top = 28.dp, bottom = 8.dp)) {
        Text(title, color = Ui.Label, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        if (subtitle != null) Text(subtitle, color = Ui.Secondary, fontSize = 15.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 2.dp))
    }
}

/** Gegroepeerde sectie met afgeronde hoeken. */
@Composable
fun Section(header: String? = null, footer: String? = null, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        if (header != null) {
            Text(header.uppercase(), color = Ui.Secondary, fontSize = 13.sp, letterSpacing = 0.4.sp, modifier = Modifier.padding(start = 16.dp, bottom = 7.dp))
        }
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Ui.Cell), content = content)
        if (footer != null) {
            Text(footer, color = Ui.Secondary, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 7.dp))
        }
    }
}

/** Rij in een sectie: titel links, waarde/schakelaar/pijl rechts. */
@Composable
fun Cell(
    title: String,
    value: String? = null,
    subtitle: String? = null,
    titleColor: Color = Ui.Label,
    valueColor: Color = Ui.Secondary,
    chevron: Boolean = false,
    divider: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = titleColor, fontSize = 17.sp)
                if (subtitle != null) Text(subtitle, color = Ui.Secondary, fontSize = 14.sp, lineHeight = 18.sp)
            }
            if (value != null) {
                Text(value, color = valueColor, fontSize = 17.sp, maxLines = 1, textAlign = TextAlign.End, modifier = Modifier.padding(start = 12.dp))
            }
            trailing()
            if (chevron) {
                Spacer(Modifier.width(8.dp))
                Icon(Glyph.CHEVRON, color = Ui.Tertiary, size = 15.dp, weight = 2.2f)
            }
        }
        if (divider) Box(Modifier.padding(start = 16.dp).fillMaxWidth().height(0.6.dp).background(Ui.Separator))
    }
}

@Composable
fun Toggle(checked: Boolean, onChange: (Boolean) -> Unit) {
    Switch(
        checked, onChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = Ui.Green,
            checkedBorderColor = Ui.Green,
            uncheckedThumbColor = Color.White,
            uncheckedTrackColor = Color(0xFF39393D),
            uncheckedBorderColor = Color(0xFF39393D),
        ),
    )
}

/** Gesegmenteerde keuze, zoals de segmented control van iOS. */
@Composable
fun Segmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .padding(12.dp)
            .fillMaxWidth()
            .height(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Ui.Fill)
            .padding(3.dp),
    ) {
        options.forEachIndexed { i, label ->
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (i == selected) Color(0xFF636366) else Color.Transparent)
                    .clickable { onSelect(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = Ui.Label, fontSize = 15.sp, fontWeight = if (i == selected) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1)
            }
        }
    }
}

/** Rondjes met de accentkleuren; de gekozen kleur krijgt een witte ring. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccentPicker(selected: Int, onSelect: (Int) -> Unit) {
    FlowRow(
        Modifier.fillMaxWidth().padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ACCENTS.forEach { a ->
            val on = a.color == selected
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .then(if (on) Modifier.border(2.5.dp, Color.White, CircleShape) else Modifier)
                    .padding(if (on) 5.dp else 0.dp)
                    .clip(CircleShape)
                    .background(Color(a.color))
                    .clickable { onSelect(a.color) },
                contentAlignment = Alignment.Center,
            ) {
                if (on) Icon(Glyph.CHECK, color = Color.White, size = 18.dp, weight = 2.6f)
            }
        }
    }
}

/** Grote knop in de accentkleur. */
@Composable
fun PrimaryButton(text: String, color: Color, onClick: () -> Unit, enabled: Boolean = true) {
    Box(
        Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(50))
            .background(if (enabled) color else Ui.Fill)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (enabled) readableOn(color) else Ui.Tertiary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Zwart of wit, afhankelijk van wat beter leest op [bg]. */
fun readableOn(bg: Color): Color {
    val l = 0.299f * bg.red + 0.587f * bg.green + 0.114f * bg.blue
    return if (l > 0.62f) Color.Black else Color.White
}
