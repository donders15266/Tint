package nl.jesse.tint

import android.app.Application
import android.appwidget.AppWidgetHost
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class TintApp : Application() {
    val prefs by lazy { Prefs(this) }
    /** Gedeelde host voor widgets van andere apps op het startscherm. */
    val widgetHost by lazy { AppWidgetHost(this, 0x7157) }
}

val Context.prefs: Prefs get() = (applicationContext as TintApp).prefs

/** Accentkleuren waaruit je kunt kiezen; Android leidt het systeempalet hiervan af. */
data class Accent(val name: String, val color: Int)

val ACCENTS = listOf(
    Accent("Nothing rood", 0xFFD71921.toInt()),
    Accent("Oranje", 0xFFFF6A13.toInt()),
    Accent("Amber", 0xFFFFB000.toInt()),
    Accent("Limoen", 0xFF9BD80F.toInt()),
    Accent("Groen", 0xFF1FBF5A.toInt()),
    Accent("Mint", 0xFF14C8B0.toInt()),
    Accent("Cyaan", 0xFF14A9E8.toInt()),
    Accent("Blauw", 0xFF3166FF.toInt()),
    Accent("Indigo", 0xFF6A4DFF.toInt()),
    Accent("Paars", 0xFFAA3DF5.toInt()),
    Accent("Roze", 0xFFFF3D8B.toInt()),
)

fun accentName(color: Int) = ACCENTS.firstOrNull { it.color == color }?.name ?: "Eigen kleur"

/**
 * Alle instellingen, bewaard in SharedPreferences en tegelijk Compose-state,
 * zodat schermen direct meebewegen als een tegel of service iets wijzigt.
 */
class Prefs(context: Context) {
    val sp: SharedPreferences = context.getSharedPreferences("tint", Context.MODE_PRIVATE)

    var accent by int("accent", ACCENTS[0].color)

    // Vaste wallpaper
    var wallStyle by int("wallStyle", 0)
    var wallTarget by int("wallTarget", 2) // 0 start, 1 vergrendeling, 2 beide
    /** Id van de wallpaper die Tint het laatst instelde, om te zien of die nog actief is. */
    var wallId by int("wallId", -1)

    // Live wallpaper
    var liveStyle by int("liveStyle", 0)
    var liveSpeed by float("liveSpeed", 1f)

    // Voortgangslijn
    var lineOn by bool("lineOn", false)
    var lineMedia by bool("lineMedia", true)
    var lineDownloads by bool("lineDownloads", true)
    var lineBottom by bool("lineBottom", false)
    var lineThickness by int("lineThickness", 3)
    var lineColor by int("lineColor", 0) // 0 accent, 1 systeemaccent, 2 wit

    // Startscherm
    var homeIcons by int("homeIcons", 2) // 0 geen, 1 origineel, 2 monochroom
    var homeClock by bool("homeClock", true)
    var favorites by string("favorites", "")

    // Uiterlijk van het startscherm
    var clockTheme by int("clockTheme", 0)
    var clockSize by int("clockSize", 1) // 0 klein, 1 middel, 2 groot
    var clockAccent by bool("clockAccent", false)
    var homeDate by bool("homeDate", true)
    var homeAlign by int("homeAlign", 0) // 0 links, 1 midden
    var infoBattery by bool("infoBattery", false)
    var infoAlarm by bool("infoAlarm", false)
    var favSize by int("favSize", 1)
    var favIcons by bool("favIcons", false)
    var homeHint by bool("homeHint", true)
    var homeDim by int("homeDim", 10) // procent verduistering over de wallpaper
    /** Alleen-lezen GitHub-token om updates uit de privé-repository te halen. */
    var updateToken by string("updateToken", "")
    /** Widgets op het startscherm: "id:hoogte" gescheiden door puntkomma's, hoogte 0–2. */
    var widgets by string("widgets", "")

    fun widgetList(): List<Pair<Int, Int>> = widgets.split(';').mapNotNull {
        val parts = it.split(':')
        parts.getOrNull(0)?.toIntOrNull()?.let { id -> id to (parts.getOrNull(1)?.toIntOrNull() ?: 1) }
    }
    fun setWidgetList(list: List<Pair<Int, Int>>) { widgets = list.joinToString(";") { "${it.first}:${it.second}" } }

    fun favoriteKeys(): List<String> = favorites.split('\n').filter { it.isNotBlank() }
    fun setFavoriteKeys(keys: List<String>) { favorites = keys.joinToString("\n") }

    private fun int(key: String, def: Int) = object : ReadWriteProperty<Any?, Int> {
        val s = mutableIntStateOf(sp.getInt(key, def))
        override fun getValue(thisRef: Any?, property: KProperty<*>) = s.intValue
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
            s.intValue = value; sp.edit().putInt(key, value).apply()
        }
    }

    private fun float(key: String, def: Float) = object : ReadWriteProperty<Any?, Float> {
        val s = mutableFloatStateOf(sp.getFloat(key, def))
        override fun getValue(thisRef: Any?, property: KProperty<*>) = s.floatValue
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Float) {
            s.floatValue = value; sp.edit().putFloat(key, value).apply()
        }
    }

    private fun bool(key: String, def: Boolean) = object : ReadWriteProperty<Any?, Boolean> {
        val s = mutableStateOf(sp.getBoolean(key, def))
        override fun getValue(thisRef: Any?, property: KProperty<*>) = s.value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
            s.value = value; sp.edit().putBoolean(key, value).apply()
        }
    }

    private fun string(key: String, def: String) = object : ReadWriteProperty<Any?, String> {
        val s = mutableStateOf(sp.getString(key, def) ?: def)
        override fun getValue(thisRef: Any?, property: KProperty<*>) = s.value
        override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
            s.value = value; sp.edit().putString(key, value).apply()
        }
    }
}
