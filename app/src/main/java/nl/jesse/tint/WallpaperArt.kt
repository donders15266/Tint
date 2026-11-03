package nl.jesse.tint

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.SweepGradient
import android.view.WindowManager
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.pow

enum class WallStyle(val label: String) { GLOW("Gloed"), GRADIENT("Verloop"), DOTS("Dots"), RING("Ring") }

/** Mengt kleur [a] met [b]; t = 0 geeft a, t = 1 geeft b. */
fun mix(a: Int, b: Int, t: Float): Int {
    val u = t.coerceIn(0f, 1f)
    fun ch(s: Int) = (((a shr s) and 0xFF) * (1 - u) + ((b shr s) and 0xFF) * u).toInt() shl s
    return (0xFF shl 24) or ch(16) or ch(8) or ch(0)
}

fun withAlpha(c: Int, a: Float) = (c and 0x00FFFFFF) or ((a.coerceIn(0f, 1f) * 255).toInt() shl 24)

/** Tekent de vaste wallpapers. Alles is relatief aan breedte en hoogte, zodat het voorbeeld klopt. */
object WallpaperArt {
    private const val BASE_DOT = 0xFF1C1C1C.toInt()

    fun draw(c: Canvas, w: Int, h: Int, style: WallStyle, accent: Int) {
        val wf = w.toFloat(); val hf = h.toFloat()
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        c.drawColor(Color.BLACK)
        when (style) {
            WallStyle.GLOW -> {
                p.shader = RadialGradient(
                    wf * 0.5f, hf * 1.05f, hf * 0.78f,
                    intArrayOf(withAlpha(accent, 0.95f), withAlpha(accent, 0.35f), withAlpha(accent, 0.08f), Color.TRANSPARENT),
                    floatArrayOf(0f, 0.38f, 0.7f, 1f), Shader.TileMode.CLAMP,
                )
                c.drawRect(0f, 0f, wf, hf, p)
                p.shader = RadialGradient(
                    wf * 0.1f, hf * 0.05f, wf * 0.9f,
                    intArrayOf(withAlpha(accent, 0.16f), Color.TRANSPARENT), null, Shader.TileMode.CLAMP,
                )
                c.drawRect(0f, 0f, wf, hf, p)
            }
            WallStyle.GRADIENT -> {
                p.shader = LinearGradient(
                    0f, 0f, 0f, hf,
                    intArrayOf(Color.BLACK, Color.BLACK, mix(accent, Color.BLACK, 0.6f), mix(accent, Color.BLACK, 0.15f)),
                    floatArrayOf(0f, 0.3f, 0.75f, 1f), Shader.TileMode.CLAMP,
                )
                c.drawRect(0f, 0f, wf, hf, p)
            }
            WallStyle.DOTS -> {
                val step = wf / 18f
                val r = step * 0.2f
                val cx = wf / 2; val cy = hf * 0.44f
                val radius = wf * 0.38f
                var y = step / 2
                while (y < hf) {
                    var x = step / 2
                    while (x < wf) {
                        val d = hypot(x - cx, y - cy) / radius
                        if (d < 1f) {
                            val i = 1f - d.pow(3)
                            p.color = mix(BASE_DOT, accent, 0.35f + 0.65f * i)
                            c.drawCircle(x, y, r * (1f + 0.35f * i), p)
                        } else {
                            p.color = BASE_DOT
                            c.drawCircle(x, y, r, p)
                        }
                        x += step
                    }
                    y += step
                }
            }
            WallStyle.RING -> {
                val cx = wf / 2; val cy = hf * 0.45f
                val radius = wf * 0.33f
                p.shader = RadialGradient(
                    cx, cy, radius * 1.9f,
                    intArrayOf(withAlpha(accent, 0.28f), withAlpha(accent, 0.1f), Color.TRANSPARENT),
                    floatArrayOf(0.35f, 0.6f, 1f), Shader.TileMode.CLAMP,
                )
                c.drawRect(0f, 0f, wf, hf, p)
                p.shader = SweepGradient(cx, cy, intArrayOf(accent, mix(accent, Color.BLACK, 0.7f), accent), null)
                p.style = Paint.Style.STROKE
                p.strokeWidth = wf * 0.055f
                c.drawCircle(cx, cy, radius, p)
                p.shader = null
                p.style = Paint.Style.FILL
                p.color = accent
                c.drawCircle(cx + radius, cy, wf * 0.0275f, p)
            }
        }
    }

    fun render(w: Int, h: Int, style: WallStyle, accent: Int): Bitmap {
        val bmp = Bitmap.createBitmap(max(w, 1), max(h, 1), Bitmap.Config.ARGB_8888)
        draw(Canvas(bmp), bmp.width, bmp.height, style, accent)
        return bmp
    }

    /** Stelt de wallpaper in volgens de instellingen. Duurt even: niet op de hoofdthread aanroepen. */
    fun apply(context: Context) {
        val prefs = context.prefs
        val bounds = context.getSystemService(WindowManager::class.java).maximumWindowMetrics.bounds
        val bmp = render(bounds.width(), bounds.height(), WallStyle.entries[prefs.wallStyle], prefs.accent)
        val flags = when (prefs.wallTarget) {
            0 -> WallpaperManager.FLAG_SYSTEM
            1 -> WallpaperManager.FLAG_LOCK
            else -> WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK
        }
        val wm = WallpaperManager.getInstance(context)
        val id = wm.setBitmap(bmp, null, true, flags)
        prefs.wallId = if (flags and WallpaperManager.FLAG_SYSTEM != 0) id else -1
        bmp.recycle()
    }

    /** Is de live wallpaper van Tint nu actief op het startscherm? */
    fun liveActive(context: Context) =
        WallpaperManager.getInstance(context).wallpaperInfo?.packageName == context.packageName

    /** Staat de vaste wallpaper van Tint nog op het startscherm? */
    fun staticActive(context: Context): Boolean {
        val id = context.prefs.wallId
        return id > 0 && runCatching {
            WallpaperManager.getInstance(context).getWallpaperId(WallpaperManager.FLAG_SYSTEM) == id
        }.getOrDefault(false)
    }
}
