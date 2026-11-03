package nl.jesse.tint

import android.app.StatusBarManager
import android.app.WallpaperManager
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon as AndroidIcon
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Tab(val label: String, val icon: Glyph) {
    WALLPAPER("Wallpaper", Glyph.IMAGE),
    LIVE("Live", Glyph.SPARK),
    LINE("Lijn", Glyph.LINE),
    HOME("Start", Glyph.HOME),
    INFO("Info", Glyph.INFO),
}

class MainActivity : ComponentActivity() {
    companion object { const val EXTRA_TAB = "tab" }

    private var tab by mutableStateOf(Tab.WALLPAPER)
    /** Telt op bij elke terugkeer naar de app, zodat toestemmingen en status opnieuw worden gelezen. */
    private var resumeKey by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        readTab(intent)
        setContent {
            val p = prefs
            Box(Modifier.fillMaxSize().background(Ui.Bg)) {
                val k = resumeKey
                when (tab) {
                    Tab.WALLPAPER -> WallpaperScreen(p, k)
                    Tab.LIVE -> LiveScreen(p, k)
                    Tab.LINE -> LineScreen(p, k)
                    Tab.HOME -> HomeTab(p, k, onAddWidget = { picking = true })
                    Tab.INFO -> InfoScreen()
                }
                if (picking) {
                    WidgetPicker(onPick = ::pickWidget, onClose = { picking = false })
                    return@Box
                }
                // Donkere band achter de statusbalk, zodat de klok leesbaar blijft boven scrollende inhoud
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Ui.Bg, Ui.Bg, Color.Transparent)))
                        .statusBarsPadding()
                        .height(14.dp)
                )
                TabBar(
                    tab, Color(p.accent), { tab = it },
                    Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 8.dp),
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readTab(intent)
    }

    override fun onResume() {
        super.onResume()
        resumeKey++
    }

    private fun readTab(intent: Intent?) {
        intent?.getStringExtra(EXTRA_TAB)?.let { name -> Tab.entries.firstOrNull { it.name == name }?.let { tab = it } }
    }

    // ---------- Widget toevoegen: kiezen → toestemming → eventueel instellen → bewaren ----------

    private var picking by mutableStateOf(false)
    private var pendingWidget = -1
    private val host get() = (application as TintApp).widgetHost
    private val awm by lazy { AppWidgetManager.getInstance(this) }

    private val bindWidget = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == RESULT_OK) configureWidget(pendingWidget) else dropPendingWidget()
    }

    private fun pickWidget(info: AppWidgetProviderInfo) {
        picking = false
        val id = host.allocateAppWidgetId()
        pendingWidget = id
        if (awm.bindAppWidgetIdIfAllowed(id, info.provider)) {
            configureWidget(id)
        } else {
            // Android vraagt eenmalig of Tint widgets mag tonen
            bindWidget.launch(
                Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                    .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
            )
        }
    }

    private fun configureWidget(id: Int) {
        val info = awm.getAppWidgetInfo(id)
        val optional = info != null && info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL != 0
        if (info?.configure != null && !optional) {
            try {
                host.startAppWidgetConfigureActivityForResult(this, id, 0, REQ_CONFIGURE, null)
                return
            } catch (e: Exception) {
                // Geen instelscherm te openen: dan zonder instellen toevoegen
            }
        }
        addWidget(id)
    }

    @Deprecated("Het instelscherm van widgets werkt alleen met onActivityResult")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_CONFIGURE) {
            if (resultCode == RESULT_OK) addWidget(pendingWidget) else dropPendingWidget()
        }
    }

    private fun addWidget(id: Int) {
        if (id == -1) return
        prefs.setWidgetList(prefs.widgetList() + (id to 1))
        pendingWidget = -1
        toast("Widget toegevoegd aan je startscherm")
    }

    private fun dropPendingWidget() {
        if (pendingWidget != -1) host.deleteAppWidgetId(pendingWidget)
        pendingWidget = -1
    }
}

private const val REQ_CONFIGURE = 41

private val ListPadding = PaddingValues(bottom = 120.dp)

private fun Context.toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()

/** Voorbeeld in de verhouding van het scherm, met afgeronde hoeken als een telefoon. */
@Composable
private fun PhoneFrame(content: @Composable (Modifier) -> Unit) {
    Box(Modifier.fillMaxWidth().padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
        content(
            Modifier
                .width(156.dp)
                .aspectRatio(1080f / 2392f)
                .clip(RoundedCornerShape(24.dp))
                .border(1.dp, Ui.Separator, RoundedCornerShape(24.dp))
        )
    }
}

// ---------- Wallpaper ----------

@Composable
fun WallpaperScreen(p: Prefs, resumeKey: Int) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val accent = p.accent
    val style = WallStyle.entries[p.wallStyle]
    val preview = remember(style, accent) { WallpaperArt.render(270, 598, style, accent).asImageBitmap() }
    var busy by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = ListPadding) {
        item { LargeTitle("Wallpaper", "Android haalt je accentkleur uit de achtergrond") }
        item { PhoneFrame { Image(preview, null, it) } }
        item { Section("Accentkleur") { AccentPicker(accent) { c -> p.accent = c } } }
        item { Section("Stijl") { Segmented(WallStyle.entries.map { it.label }, p.wallStyle, { p.wallStyle = it }) } }
        item {
            Section("Instellen op") {
                Segmented(listOf("Start", "Vergrendeling", "Beide"), p.wallTarget, { p.wallTarget = it })
            }
        }
        item {
            PrimaryButton(if (busy) "Bezig…" else "Instellen als wallpaper", Color(accent), enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    val r = withContext(Dispatchers.Default) { runCatching { WallpaperArt.apply(ctx.applicationContext) } }
                    busy = false
                    ctx.toast(r.fold({ "Wallpaper ingesteld. Het systeempalet volgt binnen enkele seconden." }, { "Mislukt: ${it.message}" }))
                }
            })
        }
        item { SystemPalette(resumeKey) }
    }
}

/** De kleuren die Android nu voor het systeem gebruikt (Material You). */
@Composable
fun SystemPalette(resumeKey: Int) {
    val ctx = LocalContext.current
    val ids = remember(resumeKey) {
        listOf(
            android.R.color.system_accent1_100, android.R.color.system_accent1_300, android.R.color.system_accent1_500,
            android.R.color.system_accent1_700, android.R.color.system_accent2_500, android.R.color.system_accent3_500,
            android.R.color.system_neutral1_800,
        ).map { ctx.getColor(it) }
    }
    Section(
        "Huidig systeempalet",
        "Dit palet gebruikt Android nu voor knoppen, schakelaars en snelle instellingen. " +
            "Volgt het niet? Kies dan in Instellingen › Wallpaper en stijl de kleuren 'van de wallpaper' in plaats van een vast thema.",
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            ids.forEach { c -> Box(Modifier.size(36.dp).clip(CircleShape).background(Color(c)).border(1.dp, Ui.Separator, CircleShape)) }
        }
    }
}

// ---------- Live wallpaper ----------

@Composable
fun LiveScreen(p: Prefs, resumeKey: Int) {
    val ctx = LocalContext.current
    val accent = p.accent
    val style = LiveStyle.entries[p.liveStyle.coerceIn(0, LiveStyle.entries.size - 1)]
    val active = remember(resumeKey) { WallpaperArt.liveActive(ctx) }
    val t by produceState(0f) {
        var last = withFrameNanos { it }
        while (true) withFrameNanos { n -> value += (n - last) / 1e9f * p.liveSpeed; last = n }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = ListPadding) {
        item { LargeTitle("Live wallpaper", "Bewegende dot-matrix die je accentkleur aan Android doorgeeft") }
        item {
            PhoneFrame { m ->
                Canvas(m) {
                    drawIntoCanvas { LiveArt.draw(it.nativeCanvas, size.width.toInt(), size.height.toInt(), t * 1f, style, accent) }
                }
            }
        }
        item {
            Section {
                Cell("Status", if (active) "Actief" else "Niet actief", valueColor = if (active) Ui.Green else Ui.Secondary, divider = false)
            }
        }
        item { Section("Beweging") { Segmented(LiveStyle.entries.map { it.label }, style.ordinal, { p.liveStyle = it }) } }
        item {
            val speed = LIVE_SPEEDS.indices.minBy { kotlin.math.abs(LIVE_SPEEDS[it].first - p.liveSpeed) }
            Section("Snelheid") { Segmented(LIVE_SPEEDS.map { it.second }, speed, { p.liveSpeed = LIVE_SPEEDS[it].first }) }
        }
        item { Section("Accentkleur", "Dezelfde kleur als bij Wallpaper. Is de live wallpaper actief, dan verandert het systeempalet direct mee.") { AccentPicker(accent) { c -> p.accent = c } } }
        item {
            PrimaryButton(if (active) "Opnieuw instellen" else "Live wallpaper instellen", Color(accent), onClick = {
                val i = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
                    .putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, ComponentName(ctx, DotWallpaper::class.java))
                runCatching { ctx.startActivity(i) }.onFailure {
                    runCatching { ctx.startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)) }
                }
            })
        }
        item {
            Text(
                "De animatie pauzeert vanzelf als het scherm uit is of een app het hele scherm vult, zodat hij weinig batterij kost.",
                color = Ui.Secondary, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(horizontal = 32.dp, vertical = 4.dp),
            )
        }
    }
}

// ---------- Voortgangslijn ----------

@Composable
fun LineScreen(p: Prefs, resumeKey: Int) {
    val ctx = LocalContext.current
    val access = remember(resumeKey) { ProgressListener.hasAccess(ctx) }
    val overlay = remember(resumeKey) { ProgressListener.canOverlay(ctx) }
    val lineColor = when (p.lineColor) {
        1 -> Color(ctx.getColor(android.R.color.system_accent1_300))
        2 -> Color.White
        else -> Color(p.accent)
    }
    val t by produceState(0f) {
        val start = withFrameNanos { it }
        while (true) withFrameNanos { n -> value = ((n - start) / 4e9f) % 1f }
    }
    val thicknesses = listOf(2, 3, 4, 6)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = ListPadding) {
        item { LargeTitle("Voortgangslijn", "Een dun lijntje langs de rand van je scherm terwijl er muziek speelt of iets downloadt") }
        item {
            Box(
                Modifier
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .fillMaxWidth()
                    .height(110.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFF0C0C0C))
                    .border(1.dp, Ui.Separator, RoundedCornerShape(24.dp)),
            ) {
                val h = (p.lineThickness).dp
                val align = if (p.lineBottom) Alignment.BottomStart else Alignment.TopStart
                Box(Modifier.align(align).fillMaxWidth().height(h).background(lineColor.copy(alpha = 0.18f)))
                Box(Modifier.align(align).fillMaxWidth(t).height(h).background(lineColor))
                Text("12:30", color = Ui.Label, fontSize = 15.sp, modifier = Modifier.align(Alignment.TopStart).padding(start = 22.dp, top = 12.dp))
            }
        }
        item {
            Section(footer = "Werkt bij elke app met een voortgangsbalk in de melding, zoals installaties in de Play Store, en bij muziek of video die afspeelt.") {
                Cell("Voortgangslijn", divider = false) { Toggle(p.lineOn) { p.lineOn = it } }
            }
        }
        item {
            Section("Toestemmingen", "Tint kijkt alleen óf er iets afspeelt of downloadt en hoe ver het is. Meldingen worden niet bewaard of verstuurd.") {
                Cell(
                    "Meldingstoegang", if (access) "Aan" else "Toestaan", valueColor = if (access) Ui.Green else Color(p.accent), chevron = true,
                    onClick = {
                        ctx.startActivity(
                            Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, ComponentName(ctx, ProgressListener::class.java).flattenToString())
                        )
                    },
                )
                Cell(
                    "Over andere apps tekenen", if (overlay) "Aan" else "Toestaan", valueColor = if (overlay) Ui.Green else Color(p.accent), chevron = true, divider = false,
                    onClick = { ctx.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))) },
                )
            }
        }
        item {
            Section("Tonen bij") {
                Cell("Muziek en video", subtitle = "Positie in het nummer of de video") { Toggle(p.lineMedia) { p.lineMedia = it } }
                Cell("Downloads en installaties", subtitle = "Bijvoorbeeld de Play Store", divider = false) { Toggle(p.lineDownloads) { p.lineDownloads = it } }
            }
        }
        item {
            Section("Uiterlijk") {
                Segmented(listOf("Boven", "Onder"), if (p.lineBottom) 1 else 0, { p.lineBottom = it == 1 })
                Segmented(thicknesses.map { "$it dp" }, thicknesses.indexOf(p.lineThickness).coerceAtLeast(0), { p.lineThickness = thicknesses[it] })
                Segmented(listOf("Accent", "Systeem", "Wit"), p.lineColor, { p.lineColor = it })
            }
        }
        item {
            PrimaryButton("Lijn 5 seconden testen", Color(p.accent), enabled = access && overlay, onClick = {
                val l = ProgressListener.instance
                if (l == null) ctx.toast("De lijn start zodra meldingstoegang aan staat") else l.demo()
            })
        }
    }
}

// ---------- Startscherm ----------

/** Startscherm-menu met de keuze voor standaard-startscherm erbij. */
@Composable
fun HomeTab(p: Prefs, resumeKey: Int, onAddWidget: () -> Unit) {
    val ctx = LocalContext.current
    val roles = ctx.getSystemService(RoleManager::class.java)
    var held by remember(resumeKey) { mutableStateOf(roles.isRoleHeld(RoleManager.ROLE_HOME)) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        held = roles.isRoleHeld(RoleManager.ROLE_HOME)
    }
    HomeSettingsScreen(p, held, onMakeDefault = {
        if (roles.isRoleAvailable(RoleManager.ROLE_HOME)) request.launch(roles.createRequestRoleIntent(RoleManager.ROLE_HOME))
        else ctx.startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
    }, onAddWidget = onAddWidget)
}

// ---------- Info ----------

@Composable
fun InfoScreen() {
    val ctx = LocalContext.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = ListPadding) {
        item { LargeTitle("Info", "Updates, tegels en wat er zonder root wel en niet kan") }
        item { UpdateSection() }
        item {
            Section("Tegels in snelle instellingen", "Accent en Stijl werken op de wallpaper van Tint (vast of live).") {
                TileCell(ctx, "Accent", "Wissel naar de volgende accentkleur", AccentTile::class.java, R.drawable.ic_tile_accent)
                TileCell(ctx, "Stijl", "Wissel de stijl van de wallpaper", StyleTile::class.java, R.drawable.ic_tile_style)
                TileCell(ctx, "Voortgangslijn", "Lijn aan of uit", LineTile::class.java, R.drawable.ic_tile_line, divider = false)
            }
        }
        item {
            Section("Kan niet zonder root") {
                listOf(
                    "Kleuren of vorm van de statusbalk en navigatiebalk" to "Die tekent het systeem zelf.",
                    "Uiterlijk van snelle instellingen en meldingen" to "Alleen het kleurenpalet volgt je wallpaper.",
                    "Een systeemkleur direct kiezen" to "Kan alleen via de wallpaper, zoals Tint doet.",
                    "Klok en indeling van het vergrendelscherm" to "Regelt Nothing OS; Tint kan wel de wallpaper zetten.",
                    "Pictogrammen in Nothing Launcher" to "Tint's eigen startscherm heeft wel monochrome pictogrammen.",
                ).forEachIndexed { i, (a, b) -> Cell(a, subtitle = b, divider = i < 4) }
            }
        }
    }
}

@Composable
private fun TileCell(ctx: Context, title: String, sub: String, cls: Class<*>, icon: Int, divider: Boolean = true) {
    Cell(title, subtitle = sub, divider = divider, value = if (Build.VERSION.SDK_INT >= 33) "Toevoegen" else null, onClick = {
        if (Build.VERSION.SDK_INT >= 33) {
            ctx.getSystemService(StatusBarManager::class.java).requestAddTileService(
                ComponentName(ctx, cls), title, AndroidIcon.createWithResource(ctx, icon), ctx.mainExecutor,
            ) { result ->
                when (result) {
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> ctx.toast("$title staat al in snelle instellingen")
                    StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> ctx.toast("$title toegevoegd")
                    else -> {}
                }
            }
        } else {
            ctx.toast("Voeg de tegel toe via het potloodje in snelle instellingen")
        }
    })
}

// ---------- Updates ----------

/** Zoekt op GitHub naar een nieuwere versie en installeert die na bevestiging. */
@Composable
fun UpdateSection() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val current = remember { Updater.currentVersion(ctx) }
    var status by remember { mutableStateOf("Nog niet gezocht") }
    var release by remember { mutableStateOf<Updater.Release?>(null) }
    var busy by remember { mutableStateOf(false) }
    val accent = Color(ctx.prefs.accent)

    fun check() {
        // Zonder token geeft GitHub voor een privé-repository geen enkele release terug
        if (ctx.prefs.updateToken.isBlank()) {
            status = "Vul eerst je GitHub-token in"
            return
        }
        busy = true
        status = "Zoeken…"
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { Updater.latest(ctx.prefs.updateToken) } }
            busy = false
            r.onSuccess { rel ->
                release = rel?.takeIf { it.versionCode > current }
                status = when {
                    rel == null -> "Geen release gevonden: kan het token deze repository lezen?"
                    rel.versionCode > current -> "Versie ${rel.versionCode} is beschikbaar"
                    else -> "Je hebt de nieuwste versie"
                }
            }.onFailure { status = "Zoeken mislukt: ${it.message}" }
        }
    }

    fun update(rel: Updater.Release) {
        // Android vraagt eenmalig of Tint apps mag installeren
        if (!ctx.packageManager.canRequestPackageInstalls()) {
            ctx.toast("Sta 'Apps installeren' toe voor Tint en tik daarna opnieuw op Bijwerken")
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")))
            return
        }
        busy = true
        scope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val apk = Updater.download(ctx, rel, ctx.prefs.updateToken) { f -> status = "Downloaden… ${(f * 100).toInt()}%" }
                    status = "Installeren…"
                    Updater.install(ctx.applicationContext, apk)
                }
            }
            busy = false
            r.onFailure { status = "Bijwerken mislukt: ${it.message}" }
        }
    }

    var editing by remember { mutableStateOf(false) }
    var tokenText by remember { mutableStateOf("") }
    val hasToken = ctx.prefs.updateToken.isNotBlank()
    Section(
        "Updates",
        "Nieuwe versies komen uit je privé-repository ${Updater.REPO} op GitHub. Daarvoor is een alleen-lezen token nodig " +
            "(GitHub › Settings › Developer settings › Fine-grained tokens, alleen deze repository, Contents: Read-only). " +
            "Android vraagt altijd om bevestiging voordat er iets wordt geïnstalleerd.",
    ) {
        Cell("Geïnstalleerde versie", "$current")
        Cell(
            "GitHub-token", if (hasToken) "Ingesteld" else "Niet ingesteld",
            valueColor = if (hasToken) Ui.Green else accent, chevron = true,
            onClick = { editing = !editing; tokenText = "" },
        )
        if (editing) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp)).background(Ui.Fill).padding(horizontal = 12.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (tokenText.isEmpty()) Text("github_pat_…", color = Ui.Tertiary, fontSize = 15.sp)
                    BasicTextField(
                        tokenText, { tokenText = it }, singleLine = true,
                        textStyle = TextStyle(color = Ui.Label, fontSize = 15.sp),
                        cursorBrush = SolidColor(accent),
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(
                    "Bewaar", color = accent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 12.dp).clickable {
                        ctx.prefs.updateToken = tokenText.trim()
                        editing = false
                        status = "Token bewaard"
                    },
                )
            }
        }
        Cell("Status", subtitle = status, divider = false)
    }
    val rel = release
    PrimaryButton(
        when {
            busy -> "Bezig…"
            rel != null -> "Bijwerken naar versie ${rel.versionCode}"
            else -> "Zoeken naar updates"
        },
        accent, enabled = !busy,
        onClick = { if (rel != null) update(rel) else check() },
    )
}
