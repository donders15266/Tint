package nl.jesse.tint

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import kotlin.concurrent.thread

abstract class TintTile : TileService() {
    protected abstract fun render(t: Tile)

    override fun onStartListening() = refresh()

    protected fun refresh() {
        val t = qsTile ?: return
        render(t)
        t.updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    protected fun openApp(tab: Tab) {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(MainActivity.EXTRA_TAB, tab.name)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    /** Vaste wallpaper opnieuw maken met de nieuwe instellingen, op de achtergrond. */
    protected fun reapplyStatic() {
        thread {
            runCatching { WallpaperArt.apply(applicationContext) }
                .onFailure { e -> mainExecutor.execute { Toast.makeText(this, "Wallpaper instellen mislukt: ${e.message}", Toast.LENGTH_LONG).show() } }
        }
    }
}

/** Wisselt naar de volgende accentkleur; de wallpaper van Tint (vast of live) en daarmee het systeempalet volgen. */
class AccentTile : TintTile() {
    override fun render(t: Tile) {
        t.label = getString(R.string.tile_accent)
        t.subtitle = accentName(prefs.accent)
        t.state = if (WallpaperArt.liveActive(this) || WallpaperArt.staticActive(this)) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
    }

    override fun onClick() {
        val live = WallpaperArt.liveActive(this)
        val static = WallpaperArt.staticActive(this)
        if (!live && !static) { openApp(Tab.WALLPAPER); return }
        val i = ACCENTS.indexOfFirst { it.color == prefs.accent }
        prefs.accent = ACCENTS[(i + 1) % ACCENTS.size].color
        if (static) reapplyStatic()
        refresh()
    }
}

/** Wisselt de stijl van de actieve Tint-wallpaper. */
class StyleTile : TintTile() {
    override fun render(t: Tile) {
        t.label = getString(R.string.tile_style)
        when {
            WallpaperArt.liveActive(this) -> {
                t.state = Tile.STATE_ACTIVE
                t.subtitle = LiveStyle.entries[prefs.liveStyle].label
            }
            WallpaperArt.staticActive(this) -> {
                t.state = Tile.STATE_ACTIVE
                t.subtitle = WallStyle.entries[prefs.wallStyle].label
            }
            else -> { t.state = Tile.STATE_INACTIVE; t.subtitle = "Geen Tint-wallpaper" }
        }
    }

    override fun onClick() {
        when {
            WallpaperArt.liveActive(this) -> prefs.liveStyle = (prefs.liveStyle + 1) % LiveStyle.entries.size
            WallpaperArt.staticActive(this) -> {
                prefs.wallStyle = (prefs.wallStyle + 1) % WallStyle.entries.size
                reapplyStatic()
            }
            else -> { openApp(Tab.WALLPAPER); return }
        }
        refresh()
    }
}

/** Voortgangslijn aan of uit. */
class LineTile : TintTile() {
    override fun render(t: Tile) {
        t.label = getString(R.string.tile_line)
        val ready = ProgressListener.hasAccess(this) && ProgressListener.canOverlay(this)
        t.state = if (prefs.lineOn && ready) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        t.subtitle = when {
            !ready -> "Toestemming nodig"
            prefs.lineOn -> "Aan"
            else -> "Uit"
        }
    }

    override fun onClick() {
        if (!ProgressListener.hasAccess(this) || !ProgressListener.canOverlay(this)) { openApp(Tab.LINE); return }
        prefs.lineOn = !prefs.lineOn
        refresh()
    }
}
