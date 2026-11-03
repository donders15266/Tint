package nl.jesse.tint

import android.annotation.SuppressLint
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.drawable.AdaptiveIconDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.UserManager
import android.text.format.DateFormat
import android.util.LruCache
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Collator
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

data class AppEntry(
    val label: String,
    val info: LauncherActivityInfo,
    val key: String,
    val cat: AppCat = AppCat.OTHER,
    val installed: Long = 0,
    val system: Boolean = false,
)

private val NL = Locale("nl", "NL")
private val HomeShadow = Shadow(Color.Black.copy(alpha = 0.55f), Offset(0f, 2f), 10f)

/** Minimalistisch startscherm: klok, vastgezette apps en een app-lijst onder een veeg omhoog. */
class HomeActivity : ComponentActivity() {
    companion object { const val EXTRA_PREVIEW = "preview" }

    private var apps by mutableStateOf<List<AppEntry>>(emptyList())
    private var drawer by mutableStateOf(false)
    private var preview by mutableStateOf(false)
    /** Telt op bij elke druk op de thuisknop, om terug te gaan naar de eerste pagina. */
    private var homePresses by mutableIntStateOf(0)
    /** Ligt er nu iets over de wallpaper (lade, bibliotheek, map)? */
    private var covered = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        readPreview(intent)
        setContent {
            HomeScreen(
                apps = apps,
                drawer = drawer,
                setDrawer = { drawer = it },
                preview = preview,
                homePresses = homePresses,
                onLaunch = ::launch,
                onCovered = { covered = it; LiveArt.covered = it },
                onExit = { finish() },
            )
        }
    }

    override fun onResume() {
        super.onResume()
        lifecycleScope.launch {
            val list = withContext(Dispatchers.IO) { loadApps() }
            apps = list
            // Pictogrammen voor de lijst en de bibliotheek alvast tekenen, zodat scrollen niets hoeft te laden
            val p = prefs
            val modes = setOfNotNull(p.homeIcons.takeIf { it != 0 }, if (p.homeIcons == 0) 2 else p.homeIcons)
            withContext(Dispatchers.Default) { IconCache.preload(list, modes, p.accent) }
        }
    }

    override fun onStart() {
        super.onStart()
        LiveArt.covered = covered
        // Widgets krijgen alleen updates zolang de host luistert
        runCatching { (application as TintApp).widgetHost.startListening() }
    }

    // Startscherm weg (app open, scherm vergrendeld): de wallpaper mag weer bewegen
    override fun onStop() {
        super.onStop()
        LiveArt.covered = false
        runCatching { (application as TintApp).widgetHost.stopListening() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readPreview(intent)
        // Thuisknop: terug naar het lege startscherm
        drawer = false
        homePresses++
    }

    private fun readPreview(intent: Intent?) {
        val isHome = getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_HOME)
        preview = !isHome && intent?.getBooleanExtra(EXTRA_PREVIEW, false) == true
    }

    private fun loadApps(): List<AppEntry> {
        val la = getSystemService(LauncherApps::class.java)
        val um = getSystemService(UserManager::class.java)
        val collator = Collator.getInstance(NL)
        return um.userProfiles.flatMap { user ->
            val serial = um.getSerialNumberForUser(user)
            la.getActivityList(null, user).map {
                val ai = it.applicationInfo
                val installed = runCatching { packageManager.getPackageInfo(ai.packageName, 0).firstInstallTime }.getOrDefault(0L)
                AppEntry(
                    it.label.toString(), it, "${it.componentName.flattenToString()}|$serial",
                    categorize(ai), installed, ai.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                )
            }
        }.sortedWith { a, b -> collator.compare(a.label, b.label) }
    }

    private fun launch(app: AppEntry) {
        runCatching {
            getSystemService(LauncherApps::class.java).startMainActivity(app.info.componentName, app.info.user, null, null)
        }
        drawer = false
    }
}

/** Opent het meldingenpaneel; dat kan alleen via een verborgen systeemfunctie. */
@SuppressLint("WrongConstant")
private fun expandNotifications(context: Context) {
    runCatching {
        val sb = context.getSystemService("statusbar")
        Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(sb)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeScreen(
    apps: List<AppEntry>,
    drawer: Boolean,
    setDrawer: (Boolean) -> Unit,
    preview: Boolean,
    homePresses: Int,
    onLaunch: (AppEntry) -> Unit,
    onCovered: (Boolean) -> Unit,
    onExit: () -> Unit,
) {
    val ctx = LocalContext.current
    val p = ctx.prefs
    var menuFor by remember { mutableStateOf<AppEntry?>(null) }
    var folder by remember { mutableStateOf<AppCat?>(null) }
    val favKeys = p.favoriteKeys()
    val pager = rememberPagerState { 2 }
    val scope = rememberCoroutineScope()

    // De live wallpaper laten pauzeren zolang hij grotendeels bedekt is
    LaunchedEffect(drawer, folder, pager.currentPage) { onCovered(drawer || folder != null || pager.currentPage != 0) }

    // Thuisknop: alles sluiten en terug naar de eerste pagina
    LaunchedEffect(homePresses) {
        if (homePresses > 0) {
            menuFor = null; folder = null
            pager.animateScrollToPage(0)
        }
    }

    // Als echt startscherm doet terug niets; als voorbeeld sluit het
    BackHandler(enabled = !drawer && folder == null && menuFor == null) {
        if (pager.currentPage != 0) scope.launch { pager.animateScrollToPage(0) } else if (preview) onExit()
    }
    BackHandler(enabled = drawer && menuFor == null) { setDrawer(false) }
    BackHandler(enabled = menuFor != null) { menuFor = null }

    Box(Modifier.fillMaxSize()) {
        HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
            if (page == 0) {
                HomePage(apps, favKeys, p, onLaunch, onLongPress = { menuFor = it }, setDrawer = setDrawer)
            } else {
                LibraryPage(apps, favKeys, p, onLaunch, onLongPress = { menuFor = it }, onSearch = { setDrawer(true) }, onOpen = { folder = it })
            }
        }

        if (!drawer) {
            Row(
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                repeat(2) { i ->
                    Box(Modifier.size(6.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = if (pager.currentPage == i) 0.95f else 0.35f)))
                }
            }
        }

        folder?.let { cat ->
            val list = remember(apps, favKeys, cat) { libraryGroups(apps, favKeys).firstOrNull { it.first == cat }?.second.orEmpty() }
            CategoryFolder(cat, list, p, onLaunch = { folder = null; onLaunch(it) }, onLongPress = { menuFor = it }, onDismiss = { folder = null })
        }

        AnimatedVisibility(
            drawer,
            enter = fadeIn(tween(180)) + slideInVertically(tween(260)) { it / 8 },
            exit = fadeOut(tween(160)) + slideOutVertically(tween(200)) { it / 8 },
        ) {
            AppDrawer(apps, favKeys, p, onLaunch, onLongPress = { menuFor = it }, onClose = { setDrawer(false) })
        }

        menuFor?.let { app ->
            AppMenu(app, pinned = app.key in favKeys, onDismiss = { menuFor = null }, onTogglePin = {
                val keys = p.favoriteKeys()
                p.setFavoriteKeys(if (app.key in keys) keys - app.key else keys + app.key)
                menuFor = null
            })
        }
    }
}

/** Eerste pagina: klok, widgets, vastgezette apps; omhoog vegen opent alle apps, omlaag de meldingen. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomePage(
    apps: List<AppEntry>,
    favKeys: List<String>,
    p: Prefs,
    onLaunch: (AppEntry) -> Unit,
    onLongPress: (AppEntry) -> Unit,
    setDrawer: (Boolean) -> Unit,
) {
    val ctx = LocalContext.current
    val favorites = favKeys.mapNotNull { k -> apps.firstOrNull { it.key == k } }
    val center = p.homeAlign == 1
    val favSize = favTextSize(p.favSize)
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = p.homeDim / 100f))
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        if (total < -120f) setDrawer(true) else if (total > 120f) expandNotifications(ctx)
                    },
                ) { _, d -> total += d }
            }
            // Lang drukken op een lege plek opent het menu om het startscherm aan te passen
            .pointerInput(Unit) {
                detectTapGestures(onLongPress = {
                    ctx.startActivity(
                        Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, Tab.HOME.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                })
            }
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 28.dp),
        horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Spacer(Modifier.height(48.dp))
        HomeHeader(p)
        Spacer(Modifier.height(20.dp))
        HomeWidgets(p, Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()))
        Spacer(Modifier.weight(1f))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp), horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start) {
            if (favorites.isEmpty() && apps.isNotEmpty()) {
                Text(
                    "Veeg omhoog en houd een app ingedrukt om hem hier vast te zetten. Veeg naar links voor de bibliotheek. Houd het scherm ingedrukt om het aan te passen.",
                    color = Color.White.copy(alpha = 0.75f), fontSize = 16.sp, lineHeight = 22.sp,
                    textAlign = if (center) TextAlign.Center else TextAlign.Start,
                    style = TextStyle(shadow = HomeShadow),
                )
            }
            favorites.forEach { app ->
                Row(
                    Modifier
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() }, indication = null,
                            onLongClick = { onLongPress(app) },
                        ) { onLaunch(app) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (p.favIcons) {
                        AppIcon(app, if (p.homeIcons == 0) 2 else p.homeIcons, p.accent, (favSize * 1.25f).dp)
                        Spacer(Modifier.width(14.dp))
                    }
                    Text(
                        app.label,
                        color = Color.White, fontSize = favSize.sp, fontWeight = FontWeight.Medium, maxLines = 1,
                        style = TextStyle(shadow = HomeShadow),
                    )
                }
            }
        }
        Spacer(Modifier.height(if (p.homeHint) 20.dp else 40.dp))
        if (p.homeHint) {
            Row(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(50))
                    .clickable { setDrawer(true) }
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Glyph.CHEVRON, Modifier.padding(end = 6.dp).rotate(-90f), color = Color.White.copy(alpha = 0.7f), size = 13.dp, weight = 2.4f)
                Text("Alle apps", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp, style = TextStyle(shadow = HomeShadow))
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppDrawer(
    apps: List<AppEntry>,
    favKeys: List<String>,
    p: Prefs,
    onLaunch: (AppEntry) -> Unit,
    onLongPress: (AppEntry) -> Unit,
    onClose: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val shown = remember(apps, query) {
        if (query.isBlank()) apps else apps.filter { it.label.contains(query.trim(), ignoreCase = true) }
    }
    val accent = Color(p.accent)
    // Omlaag trekken bovenaan de lijst sluit de lade
    val pullToClose = remember {
        object : NestedScrollConnection {
            var pull = 0f
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && available.y > 0) {
                    pull += available.y
                    if (pull > 200f) { pull = 0f; onClose() }
                } else if (consumed.y != 0f) pull = 0f
                return Offset.Zero
            }
            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity { pull = 0f; return Velocity.Zero }
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .imePadding(),
    ) {
        Box(
            Modifier
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp)
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(50))
                .background(Ui.Fill)
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (query.isEmpty()) Text("Zoek apps", color = Ui.Tertiary, fontSize = 17.sp)
            BasicTextField(
                query, { query = it },
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 17.sp),
                cursorBrush = SolidColor(accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { shown.firstOrNull()?.let(onLaunch) }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        LazyColumn(
            Modifier.fillMaxSize().nestedScroll(pullToClose),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 40.dp),
        ) {
            items(shown, key = { it.key }) { app ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .combinedClickable(onLongClick = { onLongPress(app) }) { onLaunch(app) }
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (p.homeIcons != 0) {
                        AppIcon(app, p.homeIcons, p.accent, 40.dp)
                        Spacer(Modifier.width(16.dp))
                    }
                    Text(app.label, color = Color.White, fontSize = 18.sp, maxLines = 1, modifier = Modifier.weight(1f))
                    if (app.key in favKeys) Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(accent))
                }
            }
        }
    }
}

@Composable
private fun AppMenu(app: AppEntry, pinned: Boolean, onDismiss: () -> Unit, onTogglePin: () -> Unit) {
    val ctx = LocalContext.current
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .navigationBarsPadding()
                .padding(16.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(Ui.Cell)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {},
        ) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                AppIcon(app, 1, 0, 44.dp)
                Spacer(Modifier.width(14.dp))
                Text(app.label, color = Ui.Label, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            }
            Box(Modifier.fillMaxWidth().height(0.6.dp).background(Ui.Separator))
            Cell(if (pinned) "Losmaken van startscherm" else "Vastzetten op startscherm", onClick = onTogglePin)
            Cell("App-info", onClick = {
                runCatching { ctx.getSystemService(LauncherApps::class.java).startAppDetailsActivity(app.info.componentName, app.info.user, null, null) }
                onDismiss()
            })
            Cell("Verwijderen", titleColor = Ui.Red, divider = false, onClick = {
                runCatching {
                    ctx.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.info.componentName.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
                onDismiss()
            })
        }
    }
}

// ---------- Pictogrammen ----------

/**
 * Pictogrammen worden één keer op een vaste maat getekend en bewaard. Zo hoeft er tijdens
 * het scrollen niets meer geladen te worden: alleen de kleinere weergaven schalen ze omlaag.
 */
object IconCache {
    /** Groot genoeg voor de grootste weergave (58 dp in de bibliotheek). */
    private const val PX = 160
    private val cache = LruCache<String, ImageBitmap>(600)

    private fun key(app: AppEntry, mode: Int, accent: Int) = "${app.key}/$mode/${if (mode == 2) accent else 0}"

    fun peek(app: AppEntry, mode: Int, accent: Int): ImageBitmap? = cache.get(key(app, mode, accent))

    fun get(app: AppEntry, mode: Int, accent: Int): ImageBitmap {
        val k = key(app, mode, accent)
        cache.get(k)?.let { return it }
        val bmp = render(app, mode, accent, PX).asImageBitmap().also { it.prepareToDraw() }
        cache.put(k, bmp)
        return bmp
    }

    /** Alle pictogrammen alvast klaarzetten, op de achtergrond. */
    fun preload(apps: List<AppEntry>, modes: Collection<Int>, accent: Int) {
        for (mode in modes) for (app in apps) runCatching { get(app, mode, accent) }
    }

    private fun render(app: AppEntry, mode: Int, accent: Int, px: Int): Bitmap {
        val d = app.info.getIcon(0)
        if (mode == 1) return d.toBitmap(px, px)
        // Monochroom: eigen tekening op een donker rondje, gekleurd in de accentkleur
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        c.drawCircle(px / 2f, px / 2f, px / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1C1C1E.toInt() })
        val glyph = if (Build.VERSION.SDK_INT >= 33) (d as? AdaptiveIconDrawable)?.monochrome?.let { monoGlyph(it, accent, px) } else null
        if (glyph != null) {
            c.drawBitmap(glyph, 0f, 0f, null)
            glyph.recycle()
        } else {
            val g = d.mutate()
            g.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
            val inset = px / 8
            g.setBounds(inset, inset, px - inset, px - inset)
            g.draw(c)
        }
        return bmp
    }

    /**
     * Tekent de monochrome laag binnen een rondje. Sommige apps leveren een laag die helemaal
     * gevuld is (dan zie je alleen een gekleurd vlak): die keuren we af, zodat de grijze versie
     * van het gewone pictogram wordt gebruikt.
     */
    private fun monoGlyph(mono: android.graphics.drawable.Drawable, accent: Int, px: Int): Bitmap? {
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(bmp)
        c.clipPath(android.graphics.Path().apply { addCircle(px / 2f, px / 2f, px / 2f, android.graphics.Path.Direction.CW) })
        // Een adaptieve laag is 108 eenheden waarvan 72 zichtbaar: dus anderhalf keer zo groot tekenen
        val m = mono.mutate()
        m.setTint(accent)
        m.setBounds(-px / 4, -px / 4, px + px / 4, px + px / 4)
        m.draw(c)
        var filled = 0
        var total = 0
        for (y in 0 until px step 8) for (x in 0 until px step 8) {
            val dx = x - px / 2f; val dy = y - px / 2f
            if (dx * dx + dy * dy > (px / 2f) * (px / 2f)) continue
            total++
            if (bmp.getPixel(x, y) ushr 24 > 128) filled++
        }
        if (total == 0 || filled > total * 0.6f) { bmp.recycle(); return null }
        return bmp
    }
}

@Composable
fun AppIcon(app: AppEntry, mode: Int, accent: Int, size: Dp) {
    // Meestal al in de cache: dan direct tekenen, zonder lege frame of coroutine
    val cached = IconCache.peek(app, mode, accent)
    val bmp = cached ?: produceState<ImageBitmap?>(null, app.key, mode, accent) {
        value = withContext(Dispatchers.Default) { runCatching { IconCache.get(app, mode, accent) }.getOrNull() }
    }.value
    if (bmp != null) Image(bmp, null, Modifier.size(size), filterQuality = FilterQuality.Medium) else Box(Modifier.size(size))
}
