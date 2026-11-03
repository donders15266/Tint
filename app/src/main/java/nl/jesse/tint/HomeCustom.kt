package nl.jesse.tint

import android.app.AlarmManager
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.os.BatteryManager
import android.os.Bundle
import android.text.format.DateFormat
import android.util.SizeF
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.Instant
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private val NL = Locale("nl", "NL")
val HomeTextShadow = Shadow(Color.Black.copy(alpha = 0.55f), Offset(0f, 2f), 10f)

enum class ClockTheme(val label: String) {
    DOTS("Dot-matrix"),
    THIN("Dun"),
    BOLD("Vet"),
    STACK("Gestapeld"),
    ANALOG("Analoog"),
}

val WIDGET_HEIGHTS = listOf(120.dp, 200.dp, 300.dp)

/** Huidige tijd, ververst op elke hele minuut. */
@Composable
fun rememberNow(): LocalDateTime {
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            delay(60_000 - System.currentTimeMillis() % 60_000)
        }
    }
    return now
}

/** De klok in het gekozen thema. [scale] verkleint hem voor voorbeelden in de instellingen. */
@Composable
fun ClockFace(theme: ClockTheme, sizeIndex: Int, accentOn: Boolean, accent: Color, now: LocalDateTime, center: Boolean, scale: Float = 1f) {
    val ctx = LocalContext.current
    val h24 = DateFormat.is24HourFormat(ctx)
    val hh = now.format(DateTimeFormatter.ofPattern(if (h24) "HH" else "h"))
    val mm = now.format(DateTimeFormatter.ofPattern("mm"))
    val sep = if (accentOn) accent else Color.White
    val style = TextStyle(shadow = HomeTextShadow, textAlign = if (center) TextAlign.Center else TextAlign.Start)
    val f = listOf(0.72f, 1f, 1.3f)[sizeIndex.coerceIn(0, 2)] * scale
    when (theme) {
        ClockTheme.DOTS, ClockTheme.THIN, ClockTheme.BOLD -> {
            val text = buildAnnotatedString {
                append(hh)
                withStyle(SpanStyle(color = sep)) { append(":") }
                append(mm)
            }
            val (size, family, weight, spacing) = when (theme) {
                ClockTheme.DOTS -> Quad(92f, Doto, FontWeight.Bold, 2f)
                ClockTheme.THIN -> Quad(96f, null, FontWeight.Thin, -3f)
                else -> Quad(86f, null, FontWeight.Black, -2f)
            }
            Text(
                text, color = Color.White, fontSize = (size * f).sp, fontFamily = family, fontWeight = weight,
                letterSpacing = (spacing * f).sp, maxLines = 1, style = style.copy(fontFeatureSettings = "tnum"),
            )
        }
        ClockTheme.STACK -> {
            Column(horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start) {
                Text(hh, color = Color.White, fontSize = (100 * f).sp, lineHeight = (96 * f).sp, fontFamily = Doto, style = style)
                Text(mm, color = sep, fontSize = (100 * f).sp, lineHeight = (96 * f).sp, fontFamily = Doto, style = style)
            }
        }
        ClockTheme.ANALOG -> AnalogDots(now, (190 * f).dp, if (accentOn) accent else Color.White)
    }
}

private data class Quad(val a: Float, val b: androidx.compose.ui.text.font.FontFamily?, val c: FontWeight, val d: Float)

/** Analoge klok van dots in Nothing-stijl: een ring van minuutstippen en wijzers van dots. */
@Composable
private fun AnalogDots(now: LocalDateTime, size: Dp, minuteColor: Color) {
    Canvas(Modifier.size(size)) {
        val r = this.size.minDimension / 2
        val c = Offset(r, r)
        drawCircle(Color.Black.copy(alpha = 0.25f), r, c)
        for (i in 0 until 60) {
            val a = i * PI / 30 - PI / 2
            val big = i % 5 == 0
            val rr = r * 0.9f
            drawCircle(
                Color.White.copy(alpha = if (big) 0.95f else 0.35f), if (big) r * 0.035f else r * 0.016f,
                Offset(c.x + cos(a).toFloat() * rr, c.y + sin(a).toFloat() * rr),
            )
        }
        fun hand(angle: Double, length: Float, dot: Float, color: Color) {
            val n = 7
            for (k in 1..n) {
                val d = length * k / n
                drawCircle(color, dot, Offset(c.x + cos(angle).toFloat() * d, c.y + sin(angle).toFloat() * d))
            }
        }
        val min = now.minute
        val hr = (now.hour % 12) + min / 60.0
        hand(hr * PI / 6 - PI / 2, r * 0.48f, r * 0.045f, Color.White)
        hand(min * PI / 30 - PI / 2, r * 0.74f, r * 0.035f, minuteColor)
        drawCircle(Color.White, r * 0.06f, c)
    }
}

/** Klok, datum en info-regel bovenaan het startscherm. */
@Composable
fun HomeHeader(p: Prefs, scale: Float = 1f) {
    val ctx = LocalContext.current
    val now = rememberNow()
    val center = p.homeAlign == 1
    val accent = Color(p.accent)
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start) {
        if (p.homeClock) ClockFace(ClockTheme.entries[p.clockTheme.coerceIn(0, ClockTheme.entries.size - 1)], p.clockSize, p.clockAccent, accent, now, center, scale)
        if (p.homeDate) {
            Text(
                now.format(DateTimeFormatter.ofPattern("EEEE d MMMM", NL)), color = Color.White.copy(alpha = 0.85f), fontSize = (19 * scale).sp,
                style = TextStyle(shadow = HomeTextShadow), modifier = Modifier.padding(start = if (center) 0.dp else 4.dp, top = 2.dp),
            )
        }
        val info = remember(now, p.infoBattery, p.infoAlarm) { infoLine(ctx, p.infoBattery, p.infoAlarm) }
        if (info.isNotEmpty()) {
            Row(Modifier.padding(start = if (center) 0.dp else 4.dp, top = (8 * scale).dp), verticalAlignment = Alignment.CenterVertically) {
                info.forEachIndexed { i, s ->
                    if (i > 0) Box(Modifier.padding(horizontal = 10.dp).size((5 * scale).dp).clip(CircleShape).background(accent))
                    Text(s, color = Color.White.copy(alpha = 0.85f), fontSize = (15 * scale).sp, style = TextStyle(shadow = HomeTextShadow))
                }
            }
        }
    }
}

private fun infoLine(ctx: Context, battery: Boolean, alarm: Boolean): List<String> {
    val out = mutableListOf<String>()
    if (battery) {
        val bm = ctx.getSystemService(BatteryManager::class.java)
        val pct = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        out += if (bm.isCharging) "$pct% · laadt" else "$pct%"
    }
    if (alarm) {
        ctx.getSystemService(AlarmManager::class.java).nextAlarmClock?.let {
            val t = Instant.ofEpochMilli(it.triggerTime).atZone(ZoneId.systemDefault())
            out += "Wekker " + t.format(DateTimeFormatter.ofPattern("EEE HH:mm", NL))
        }
    }
    return out
}

// ---------- Widgets ----------

/** De widgets op het startscherm, onder elkaar, met afgeronde hoeken. */
@Composable
fun HomeWidgets(p: Prefs, modifier: Modifier = Modifier) {
    val list = p.widgetList()
    if (list.isEmpty()) return
    val ctx = LocalContext.current
    val awm = remember { AppWidgetManager.getInstance(ctx) }
    val host = (ctx.applicationContext as TintApp).widgetHost
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        list.forEach { (id, h) ->
            val info = remember(id) { awm.getAppWidgetInfo(id) } ?: return@forEach
            val height = WIDGET_HEIGHTS[h.coerceIn(0, 2)]
            val density = LocalDensity.current
            AndroidView(
                factory = { c ->
                    host.createView(c, id, info).apply {
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    }
                },
                update = { v -> sizeWidget(v, density.density) },
                modifier = Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(24.dp)),
            )
        }
    }
}

/** Vertelt de widget hoe groot hij wordt getoond, zodat hij de juiste indeling kiest. */
private fun sizeWidget(v: AppWidgetHostView, density: Float) {
    v.post {
        if (v.width == 0) return@post
        val w = v.width / density; val h = v.height / density
        v.updateAppWidgetSize(Bundle(), listOf(SizeF(w, h)))
    }
}

data class ProviderEntry(val info: AppWidgetProviderInfo, val label: String, val app: String)

/** Lijst met alle widgets die geïnstalleerde apps aanbieden, gegroepeerd per app. */
@Composable
fun WidgetPicker(onPick: (AppWidgetProviderInfo) -> Unit, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val ctx = LocalContext.current
    val providers by produceState<List<ProviderEntry>?>(null) {
        value = withContext(Dispatchers.IO) {
            val pm = ctx.packageManager
            AppWidgetManager.getInstance(ctx).installedProviders.map {
                val app = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(it.provider.packageName, 0)).toString() }.getOrDefault(it.provider.packageName)
                ProviderEntry(it, it.loadLabel(pm) ?: app, app)
            }.sortedWith(compareBy({ it.app.lowercase() }, { it.label.lowercase() }))
        }
    }
    Column(Modifier.fillMaxSize().background(Ui.Bg)) {
        Row(Modifier.statusBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Widget kiezen", color = Ui.Label, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text("Sluiten", color = Ui.Secondary, fontSize = 17.sp, modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = onClose).padding(10.dp))
        }
        val list = providers
        if (list == null) {
            Text("Laden…", color = Ui.Secondary, fontSize = 15.sp, modifier = Modifier.padding(20.dp))
            return@Column
        }
        LazyColumn(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 40.dp)) {
            list.groupBy { it.app }.forEach { (app, items) ->
                item(key = "h_$app") {
                    Text(app.uppercase(), color = Ui.Secondary, fontSize = 13.sp, letterSpacing = 0.4.sp, modifier = Modifier.padding(start = 16.dp, top = 18.dp, bottom = 7.dp))
                }
                items(items, key = { it.info.provider.flattenToString() }) { e ->
                    Row(
                        Modifier
                            .padding(vertical = 4.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Ui.Cell)
                            .clickable { onPick(e.info) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        WidgetPreview(e.info)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e.label, color = Ui.Label, fontSize = 17.sp)
                            if (e.info.targetCellWidth > 0 && e.info.targetCellHeight > 0) {
                                Text("${e.info.targetCellWidth} × ${e.info.targetCellHeight}", color = Ui.Secondary, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetPreview(info: AppWidgetProviderInfo) {
    val ctx = LocalContext.current
    val img by produceState<ImageBitmap?>(null, info.provider) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val d = info.loadPreviewImage(ctx, 0) ?: info.loadIcon(ctx, 0)
                val w = d.intrinsicWidth.coerceIn(1, 400); val h = d.intrinsicHeight.coerceIn(1, 400)
                d.toBitmap(w, h).asImageBitmap()
            }.getOrNull()
        }
    }
    Box(Modifier.size(72.dp).clip(RoundedCornerShape(14.dp)).background(Ui.Fill), contentAlignment = Alignment.Center) {
        img?.let { Image(it, null, Modifier.size(64.dp)) }
    }
}

// ---------- Menu in de app ----------

/** Een rij kaartjes waarin elk klokthema zichtbaar is; tik om te kiezen. */
@Composable
private fun ClockThemePicker(p: Prefs) {
    val now = rememberNow()
    val accent = Color(p.accent)
    LazyRow(contentPadding = PaddingValues(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(ClockTheme.entries) { t ->
            val on = p.clockTheme == t.ordinal
            Column(
                Modifier
                    .width(150.dp)
                    .height(150.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black)
                    .border(if (on) 2.dp else 1.dp, if (on) accent else Ui.Separator, RoundedCornerShape(20.dp))
                    .clickable { p.clockTheme = t.ordinal }
                    .padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    ClockFace(t, 1, p.clockAccent, accent, now, center = true, scale = if (t == ClockTheme.ANALOG) 0.5f else 0.38f)
                }
                Text(t.label, color = if (on) accent else Ui.Secondary, fontSize = 13.sp, fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal)
            }
        }
    }
}

/** Voorbeeld van de bovenkant van het startscherm met de huidige keuzes. */
@Composable
private fun HomePreview(p: Prefs) {
    Box(
        Modifier
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(
                androidx.compose.ui.graphics.Brush.radialGradient(
                    listOf(Color(p.accent).copy(alpha = 0.35f), Color.Black), radius = 900f,
                    center = Offset(900f, 900f),
                )
            )
            .border(1.dp, Ui.Separator, RoundedCornerShape(28.dp))
            .padding(22.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            HomeHeader(p, scale = 0.62f)
            Spacer(Modifier.height(18.dp))
            val names = listOf("WhatsApp", "Spotify", "Camera")
            names.forEach {
                Text(
                    it, color = Color.White, fontSize = (favTextSize(p.favSize) * 0.62f).sp, fontWeight = FontWeight.Medium,
                    textAlign = if (p.homeAlign == 1) TextAlign.Center else TextAlign.Start, modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

fun favTextSize(i: Int) = listOf(22f, 30f, 38f)[i.coerceIn(0, 2)]

@Composable
fun HomeSettingsScreen(
    p: Prefs,
    held: Boolean,
    onMakeDefault: () -> Unit,
    onAddWidget: () -> Unit,
) {
    val ctx = LocalContext.current
    val awm = remember { AppWidgetManager.getInstance(ctx) }
    val pm = ctx.packageManager
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 120.dp)) {
        item { LargeTitle("Startscherm", "Klok, widgets en indeling van je eigen startscherm") }
        item { HomePreview(p) }
        item {
            Section {
                Cell("Standaard startscherm", if (held) "Tint" else "Ander", valueColor = if (held) Ui.Green else Ui.Secondary)
                Cell("Startscherm bekijken", chevron = true, divider = false, onClick = {
                    ctx.startActivity(android.content.Intent(ctx, HomeActivity::class.java).putExtra(HomeActivity.EXTRA_PREVIEW, true))
                })
            }
        }
        if (!held) item { PrimaryButton("Instellen als startscherm", Color(p.accent), onClick = onMakeDefault) }

        item { SectionHeader("Klokthema") }
        item { ClockThemePicker(p) }
        item {
            Section {
                Segmented(listOf("Klein", "Middel", "Groot"), p.clockSize, { p.clockSize = it })
                Segmented(listOf("Links", "Midden"), p.homeAlign, { p.homeAlign = it })
                Cell("Klok tonen", onClick = { p.homeClock = !p.homeClock }) { Toggle(p.homeClock) { p.homeClock = it } }
                Cell("Accentkleur in de klok", subtitle = "Dubbele punt, minuten of minutenwijzer", onClick = { p.clockAccent = !p.clockAccent }) { Toggle(p.clockAccent) { p.clockAccent = it } }
                Cell("Datum tonen", divider = false, onClick = { p.homeDate = !p.homeDate }) { Toggle(p.homeDate) { p.homeDate = it } }
            }
        }
        item {
            Section("Info onder de klok") {
                Cell("Batterij", onClick = { p.infoBattery = !p.infoBattery }) { Toggle(p.infoBattery) { p.infoBattery = it } }
                Cell("Volgende wekker", divider = false, onClick = { p.infoAlarm = !p.infoAlarm }) { Toggle(p.infoAlarm) { p.infoAlarm = it } }
            }
        }
        item {
            val list = p.widgetList()
            Section("Widgets", "Widgets van je andere apps, onder de klok. Je kiest ze uit alles wat je apps aanbieden.") {
                list.forEachIndexed { i, (id, h) ->
                    val info = remember(id) { awm.getAppWidgetInfo(id) }
                    val name = info?.loadLabel(pm) ?: "Widget"
                    Column {
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(name, color = Ui.Label, fontSize = 17.sp, modifier = Modifier.weight(1f))
                            if (i > 0) SmallButton("Omhoog") { p.setWidgetList(list.toMutableList().apply { add(i - 1, removeAt(i)) }) }
                            SmallButton("Verwijder", Ui.Red) {
                                (ctx.applicationContext as TintApp).widgetHost.deleteAppWidgetId(id)
                                p.setWidgetList(list.filterIndexed { j, _ -> j != i })
                            }
                        }
                        Segmented(listOf("Laag", "Middel", "Hoog"), h, { nh -> p.setWidgetList(list.mapIndexed { j, e -> if (j == i) e.first to nh else e }) })
                        Box(Modifier.padding(start = 16.dp).fillMaxWidth().height(0.6.dp).background(Ui.Separator))
                    }
                }
                Cell("Widget toevoegen", titleColor = Color(p.accent), chevron = true, divider = false, onClick = onAddWidget)
            }
        }
        item {
            Section("Vastgezette apps", "Houd een app ingedrukt in de app-lijst om hem vast te zetten. Houd het startscherm ingedrukt om dit menu te openen.") {
                Segmented(listOf("Klein", "Middel", "Groot"), p.favSize, { p.favSize = it })
                Cell("Pictogrammen naast de namen", onClick = { p.favIcons = !p.favIcons }) { Toggle(p.favIcons) { p.favIcons = it } }
                Cell("Hint 'Alle apps' onderaan", onClick = { p.homeHint = !p.homeHint }) { Toggle(p.homeHint) { p.homeHint = it } }
                Cell("Vastgezette apps", "${p.favoriteKeys().size}")
                Cell("Vastgezette apps wissen", titleColor = Ui.Red, divider = false, onClick = { p.favorites = "" })
            }
        }
        item {
            Section("Wallpaper verduisteren", "Maakt tekst en widgets beter leesbaar op een drukke achtergrond.") {
                val steps = listOf(0, 20, 40, 60)
                Segmented(steps.map { "$it%" }, steps.indexOf(p.homeDim).coerceAtLeast(0), { p.homeDim = steps[it] })
            }
        }
        item {
            Section("Pictogrammen in app-lijst en bibliotheek", "Monochroom kleurt de pictogrammen in je accentkleur.") {
                Segmented(listOf("Geen", "Origineel", "Monochroom"), p.homeIcons, { p.homeIcons = it })
            }
        }
        item {
            Text(
                "Terug naar Nothing Launcher: Instellingen › Apps › Standaard-apps › Startscherm-app.",
                color = Ui.Secondary, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text.uppercase(), color = Ui.Secondary, fontSize = 13.sp, letterSpacing = 0.4.sp, modifier = Modifier.padding(start = 32.dp, top = 14.dp))
}

@Composable
private fun SmallButton(text: String, color: Color = Ui.Label, onClick: () -> Unit) {
    Text(
        text, color = color, fontSize = 15.sp,
        modifier = Modifier.padding(start = 6.dp).clip(RoundedCornerShape(50)).background(Ui.Fill).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Suppress("unused")
private fun ColumnScope.unused() = Unit
