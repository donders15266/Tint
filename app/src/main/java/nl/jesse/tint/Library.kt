package nl.jesse.tint

import android.content.pm.ApplicationInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Dot-matrix-lettertype in Nothing-stijl. */
val Doto = FontFamily(Font(R.font.doto, FontWeight.Bold))

/** Kaartkleuren in Nothing-stijl: diep zwart met een fijne grijze rand. */
private val CardBg = Color(0xFF0E0E0E)
private val CardBorder = Color(0xFF2A2A2A)

enum class AppCat(val label: String) {
    PINNED("Vastgezet"),
    RECENT("Nieuw"),
    SOCIAL("Sociaal"),
    PRODUCTIVITY("Productiviteit"),
    AUDIO("Muziek en audio"),
    VIDEO("Video"),
    PHOTO("Foto's"),
    TRAVEL("Reizen en kaarten"),
    FINANCE("Financiën"),
    SHOPPING("Winkelen"),
    NEWS("Nieuws"),
    GAMES("Games"),
    SYSTEM("Systeem"),
    OTHER("Overig"),
}

/** Trefwoorden in pakketnamen voor apps die zelf geen categorie opgeven. */
private val KEYWORDS: List<Pair<AppCat, List<String>>> = listOf(
    AppCat.FINANCE to listOf("bank", "ing.mobile", "rabo", "abnamro", "bunq", "revolut", "paypal", "tikkie", "knab", "triodos", "wise", "degiro", "bitvavo", "klarna"),
    AppCat.SOCIAL to listOf("whatsapp", "telegram", "signal", "instagram", "facebook", "twitter", "snapchat", "reddit", "discord", "linkedin", "messenger", "threads", "bluesky", "mastodon", "zhiliaoapp", "tiktok", "mms", "messaging", "contacts", "dialer"),
    AppCat.AUDIO to listOf("spotify", "music", "podcast", "soundcloud", "deezer", "tidal", "audible", "shazam", "radio", "recorder"),
    AppCat.VIDEO to listOf("youtube", "netflix", "disney", "videoland", "avod", "hbo", "npo", "twitch", "vlc", "plex", "video"),
    AppCat.PHOTO to listOf("camera", "photos", "gallery", "lightroom", "snapseed", "pinterest"),
    AppCat.TRAVEL to listOf("maps", "waze", "nl.ns", "9292", "ubercab", "bolt", "booking", "airbnb", "flitsmeister", "weather", "buienradar", "anwb", "klm"),
    AppCat.SHOPPING to listOf("com.bol", "mshop", "albert", "jumbo", "thuisbezorgd", "takeaway", "marktplaats", "vinted", "zalando", "coolblue", "aliexpress", "temu", "vending", "lidl"),
    AppCat.NEWS to listOf("nu.android", "sanoma", "nos.", "news", "telegraaf", "volkskrant", "nrc", "rtl"),
    AppCat.PRODUCTIVITY to listOf("docs", "sheets", "drive", "office", "outlook", "gmail", "mail", "calendar", "notion", "todo", "keep", "teams", "slack", "zoom", "dropbox", "onenote", "claude", "anthropic", "chatgpt", "openai", "gemini", "notes", "calculator", "deskclock", "clock", "files", "documentsui", "translate"),
)

fun categorize(info: ApplicationInfo): AppCat {
    when (info.category) {
        ApplicationInfo.CATEGORY_GAME -> return AppCat.GAMES
        ApplicationInfo.CATEGORY_AUDIO -> return AppCat.AUDIO
        ApplicationInfo.CATEGORY_VIDEO -> return AppCat.VIDEO
        ApplicationInfo.CATEGORY_IMAGE -> return AppCat.PHOTO
        ApplicationInfo.CATEGORY_SOCIAL -> return AppCat.SOCIAL
        ApplicationInfo.CATEGORY_NEWS -> return AppCat.NEWS
        ApplicationInfo.CATEGORY_MAPS -> return AppCat.TRAVEL
        ApplicationInfo.CATEGORY_PRODUCTIVITY -> return AppCat.PRODUCTIVITY
    }
    val pkg = info.packageName.lowercase()
    KEYWORDS.firstOrNull { (_, words) -> words.any { it in pkg } }?.let { return it.first }
    return if (info.flags and ApplicationInfo.FLAG_SYSTEM != 0) AppCat.SYSTEM else AppCat.OTHER
}

/** Groepen voor de bibliotheek: vastgezet, nieuw geïnstalleerd en daarna per categorie. */
fun libraryGroups(apps: List<AppEntry>, favKeys: List<String>): List<Pair<AppCat, List<AppEntry>>> {
    val groups = mutableListOf<Pair<AppCat, List<AppEntry>>>()
    val pinned = favKeys.mapNotNull { k -> apps.firstOrNull { it.key == k } }
    if (pinned.isNotEmpty()) groups += AppCat.PINNED to pinned
    val recent = apps.filter { it.installed > 0 && !it.system }.sortedByDescending { it.installed }.take(8)
    if (recent.isNotEmpty()) groups += AppCat.RECENT to recent
    val byCat = apps.groupBy { it.cat }
    AppCat.entries.filter { it != AppCat.PINNED && it != AppCat.RECENT }.forEach { c ->
        byCat[c]?.takeIf { it.isNotEmpty() }?.let { groups += c to it }
    }
    return groups
}

/** App-bibliotheek zoals op iOS, maar zwart met dot-matrix-titels zoals Nothing. */
@Composable
fun LibraryPage(
    apps: List<AppEntry>,
    favKeys: List<String>,
    p: Prefs,
    onLaunch: (AppEntry) -> Unit,
    onLongPress: (AppEntry) -> Unit,
    onSearch: () -> Unit,
    onOpen: (AppCat) -> Unit,
) {
    val groups = remember(apps, favKeys) { libraryGroups(apps, favKeys) }
    val mode = if (p.homeIcons == 0) 2 else p.homeIcons
    LazyVerticalGrid(
        GridCells.Fixed(2),
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.72f)),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 120.dp),
        horizontalArrangement = Arrangement.spacedBy(18.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item(span = { GridItemSpan(2) }) {
            Column(Modifier.statusBarsPadding().padding(top = 28.dp, bottom = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("BIBLIOTHEEK", color = Color.White, fontSize = 34.sp, fontFamily = Doto, letterSpacing = 1.sp)
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.size(9.dp).clip(CircleShape).background(Color(p.accent)))
                }
                Spacer(Modifier.height(16.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(50))
                        .background(CardBg)
                        .border(1.dp, CardBorder, RoundedCornerShape(50))
                        .clickable(onClick = onSearch)
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    Text("Zoek in bibliotheek", color = Ui.Tertiary, fontSize = 17.sp)
                }
            }
        }
        items(groups, key = { it.first.name }) { (cat, list) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CategoryCard(list, mode, p.accent, onLaunch, onLongPress) { onOpen(cat) }
                Text(
                    cat.label, color = Ui.Secondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 7.dp, bottom = 10.dp),
                )
            }
        }
    }
}

/** Vierkante kaart met drie grote pictogrammen en rechtsonder een clustertje van de rest. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CategoryCard(
    list: List<AppEntry>,
    mode: Int,
    accent: Int,
    onLaunch: (AppEntry) -> Unit,
    onLongPress: (AppEntry) -> Unit,
    onOpen: () -> Unit,
) {
    val big = if (list.size <= 4) list else list.take(3)
    Column(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(30.dp))
            .background(CardBg)
            .border(1.dp, CardBorder, RoundedCornerShape(30.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        for (row in 0 until 2) {
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.SpaceBetween) {
                for (col in 0 until 2) {
                    val i = row * 2 + col
                    Box(Modifier.weight(1f).fillMaxSize(), contentAlignment = Alignment.Center) {
                        when {
                            i < big.size -> {
                                val app = big[i]
                                Box(
                                    Modifier
                                        .clip(CircleShape)
                                        .combinedClickable(onLongClick = { onLongPress(app) }) { onLaunch(app) },
                                ) { AppIcon(app, mode, accent, 58.dp) }
                            }
                            i == 3 && list.size > 4 -> Cluster(list.drop(3).take(4), mode, accent, onOpen)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Cluster(apps: List<AppEntry>, mode: Int, accent: Int, onOpen: () -> Unit) {
    Column(
        Modifier.size(58.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onOpen),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        apps.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                pair.forEach { AppIcon(it, mode, accent, 25.dp) }
                if (pair.size == 1) Spacer(Modifier.size(25.dp))
            }
        }
    }
}

/** Geopende categorie: alle apps als raster met namen. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CategoryFolder(
    cat: AppCat,
    apps: List<AppEntry>,
    p: Prefs,
    onLaunch: (AppEntry) -> Unit,
    onLongPress: (AppEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val mode = if (p.homeIcons == 0) 2 else p.homeIcons
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(34.dp))
                .background(CardBg)
                .border(1.dp, CardBorder, RoundedCornerShape(34.dp))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
                .padding(vertical = 22.dp, horizontal = 12.dp),
        ) {
            Row(Modifier.padding(start = 12.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(cat.label.uppercase(), color = Color.White, fontSize = 24.sp, fontFamily = Doto, letterSpacing = 1.sp)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(7.dp).clip(CircleShape).background(Color(p.accent)))
            }
            LazyVerticalGrid(
                GridCells.Fixed(4),
                Modifier.fillMaxWidth().height(rowsHeight(apps.size)),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(apps, key = { it.key }) { app ->
                    Column(
                        Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .combinedClickable(onLongClick = { onLongPress(app) }) { onLaunch(app) }
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        AppIcon(app, mode, p.accent, 54.dp)
                        Text(
                            app.label, color = Ui.Secondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Hoogte voor het raster: groeit mee met het aantal apps, maar hooguit vijf rijen (daarna scrollen). */
private fun rowsHeight(count: Int): Dp {
    val rows = ((count + 3) / 4).coerceIn(1, 5)
    return (rows * 84 + (rows - 1) * 14).dp
}
