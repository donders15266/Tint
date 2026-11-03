package nl.jesse.tint

import android.app.WallpaperColors
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin

enum class LiveStyle(val label: String) { WAVE("Golf"), RAIN("Regen"), PULSE("Puls") }

val LIVE_SPEEDS = listOf(0.5f to "Rustig", 1f to "Normaal", 1.8f to "Snel")

/** Dot-matrix in Nothing-stijl die zacht oplicht in de accentkleur. */
object LiveArt {
    private const val BASE = 0xFF1A1A1A.toInt()
    private val uiPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /**
     * Staat het startscherm van Tint iets over de wallpaper te tonen (lade, bibliotheek)?
     * Dan hoeft de animatie niet door te lopen en blijft er rekentijd over voor soepel scrollen.
     */
    @Volatile var covered = false

    fun draw(c: Canvas, w: Int, h: Int, t: Float, style: LiveStyle, accent: Int, paint: Paint = uiPaint) {
        c.drawColor(Color.BLACK)
        val step = w / 20f
        val r = step * 0.19f
        val cols = (w / step).toInt() + 1
        val rows = (h / step).toInt() + 1
        val ox = (w - (cols - 1) * step) / 2
        val oy = (h - (rows - 1) * step) / 2
        val cc = (cols - 1) / 2f; val cr = rows * 0.45f
        for (row in 0 until rows) for (col in 0 until cols) {
            val i = when (style) {
                LiveStyle.WAVE -> {
                    val v = 0.5f + 0.5f * sin(col * 0.33f + row * 0.17f - t * 1.5f)
                    ((v - 0.72f) / 0.28f).coerceIn(0f, 1f)
                }
                LiveStyle.RAIN -> {
                    val hsh = (col * 73856093) xor 19349663
                    val speed = 5f + (hsh and 7)
                    val span = rows + 14f
                    val head = ((t * speed + (hsh ushr 4 and 255)) % span) - 6f
                    val d = head - row
                    if (d in 0f..7f) (1f - d / 7f).let { it * it } else 0f
                }
                LiveStyle.PULSE -> {
                    val d = hypot(col - cc, row - cr)
                    val wave = (t * 5f) % 26f
                    exp(-((d - wave) * (d - wave)) / 3f) * (1f - wave / 26f)
                }
            }
            val x = ox + col * step; val y = oy + row * step
            if (i > 0.02f) {
                paint.color = mix(BASE, accent, i)
                c.drawCircle(x, y, r * (1f + 0.3f * i), paint)
            } else {
                paint.color = BASE
                c.drawCircle(x, y, r, paint)
            }
        }
    }
}

class DotWallpaper : WallpaperService() {
    override fun onCreateEngine(): Engine = DotEngine()

    /**
     * Tekent op een eigen thread: de wallpaper draait in hetzelfde proces als het startscherm
     * van Tint, en zo hoeft de hoofdthread nooit op de animatie te wachten.
     */
    inner class DotEngine : Engine(), SharedPreferences.OnSharedPreferenceChangeListener {
        private val thread = HandlerThread("TintDots").apply { start() }
        private val handler = Handler(thread.looper)
        private val lock = Any()
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        @Volatile private var visible = false
        private var surfaceOk = false
        private var w = 0
        private var h = 0
        private var t = 0f
        private var last = 0L
        private val frame = Runnable { drawFrame() }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            prefs.sp.registerOnSharedPreferenceChangeListener(this)
        }

        override fun onDestroy() {
            prefs.sp.unregisterOnSharedPreferenceChangeListener(this)
            handler.removeCallbacks(frame)
            thread.quitSafely()
            super.onDestroy()
        }

        override fun onVisibilityChanged(v: Boolean) {
            visible = v
            redraw()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            synchronized(lock) { w = width; h = height; surfaceOk = true }
            redraw()
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            // Pas terugkeren als er niet meer getekend wordt
            synchronized(lock) { surfaceOk = false }
            handler.removeCallbacks(frame)
            super.onSurfaceDestroyed(holder)
        }

        // Hieruit haalt Android het systeempalet: de accentkleur wordt de primaire kleur
        override fun onComputeColors(): WallpaperColors {
            val a = prefs.accent
            return WallpaperColors(
                Color.valueOf(a), Color.valueOf(mix(a, Color.BLACK, 0.4f)), Color.valueOf(Color.BLACK),
                WallpaperColors.HINT_SUPPORTS_DARK_THEME,
            )
        }

        override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
            if (key == "accent") notifyColorsChanged()
            if (key == "accent" || key == "liveStyle" || key == "liveSpeed") redraw()
        }

        private fun redraw() {
            handler.removeCallbacks(frame)
            last = SystemClock.uptimeMillis()
            handler.post(frame)
        }

        private fun drawFrame() {
            val now = SystemClock.uptimeMillis()
            // Bedekt door de lade of bibliotheek: stilstaan en af en toe kijken of hij weer zichtbaar is
            if (LiveArt.covered && t > 0f) {
                last = now
                if (visible) handler.postDelayed(frame, 250)
                return
            }
            t += (now - last).coerceIn(0, 100) / 1000f * prefs.liveSpeed
            last = now
            synchronized(lock) {
                if (!surfaceOk || w == 0 || h == 0) return
                val holder = surfaceHolder
                val c = try { holder.lockHardwareCanvas() } catch (e: Exception) { null } ?: return
                try {
                    LiveArt.draw(c, w, h, t, LiveStyle.entries[prefs.liveStyle.coerceIn(0, LiveStyle.entries.size - 1)], prefs.accent, paint)
                } finally {
                    holder.unlockCanvasAndPost(c)
                }
            }
            if (visible) handler.postDelayed(frame, 40)
        }
    }
}

