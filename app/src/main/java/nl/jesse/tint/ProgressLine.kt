package nl.jesse.tint

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import kotlin.math.abs

/**
 * Leest de voortgang van afspelende media en van meldingen met een voortgangsbalk
 * (zoals downloads en installaties in de Play Store) en tekent die als dun lijntje
 * langs de rand van het scherm.
 */
class ProgressListener : NotificationListenerService(), SharedPreferences.OnSharedPreferenceChangeListener {
    companion object {
        var instance: ProgressListener? = null
            private set

        fun hasAccess(context: Context): Boolean {
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
            val me = ComponentName(context, ProgressListener::class.java)
            return flat.split(':').any { ComponentName.unflattenFromString(it) == me }
        }

        fun canOverlay(context: Context) = Settings.canDrawOverlays(context)
    }

    private val handler = Handler(Looper.getMainLooper())
    private val downloads = LinkedHashMap<String, Float>() // -1 = onbepaald
    private var controllers: List<MediaController> = emptyList()
    private var line: LineView? = null
    private var demoUntil = 0L
    private var demoStart = 0L
    private val poll = Runnable { update() }
    private val mediaCallback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = update()
        override fun onMetadataChanged(metadata: MediaMetadata?) = update()
    }
    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list -> setControllers(list ?: emptyList()) }

    override fun onListenerConnected() {
        instance = this
        prefs.sp.registerOnSharedPreferenceChangeListener(this)
        runCatching { activeNotifications?.forEach { read(it) } }
        val msm = getSystemService(MediaSessionManager::class.java)
        val me = ComponentName(this, ProgressListener::class.java)
        runCatching {
            msm.addOnActiveSessionsChangedListener(sessionsListener, me, handler)
            setControllers(msm.getActiveSessions(me))
        }
        update()
    }

    override fun onListenerDisconnected() {
        instance = null
        prefs.sp.unregisterOnSharedPreferenceChangeListener(this)
        runCatching { getSystemService(MediaSessionManager::class.java).removeOnActiveSessionsChangedListener(sessionsListener) }
        setControllers(emptyList())
        handler.removeCallbacks(poll)
        hideLine()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) { read(sbn); update() }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (downloads.remove(sbn.key) != null) update()
    }

    override fun onSharedPreferenceChanged(sp: SharedPreferences?, key: String?) {
        if (key?.startsWith("line") == true || key == "accent") {
            hideLine() // opnieuw opbouwen met de nieuwe positie, dikte of kleur
            update()
        }
    }

    /** Laat de lijn een paar seconden vollopen, om te zien hoe hij eruitziet. */
    fun demo() {
        demoStart = SystemClock.uptimeMillis()
        demoUntil = demoStart + 5000
        update()
    }

    private fun read(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val ex = sbn.notification.extras
        val max = ex.getInt(Notification.EXTRA_PROGRESS_MAX, 0)
        val indeterminate = ex.getBoolean(Notification.EXTRA_PROGRESS_INDETERMINATE, false)
        when {
            max > 0 -> downloads[sbn.key] = ex.getInt(Notification.EXTRA_PROGRESS, 0).toFloat() / max
            indeterminate -> downloads[sbn.key] = -1f
            else -> downloads.remove(sbn.key)
        }
    }

    private fun setControllers(list: List<MediaController>) {
        controllers.forEach { it.unregisterCallback(mediaCallback) }
        controllers = list
        list.forEach { it.registerCallback(mediaCallback, handler) }
        update()
    }

    /** Voortgang van de eerste speler die nu afspeelt, of null. */
    private fun mediaProgress(): Float? {
        for (c in controllers) {
            val state = c.playbackState ?: continue
            if (state.state != PlaybackState.STATE_PLAYING) continue
            val duration = c.metadata?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L
            if (duration <= 0) continue
            val pos = state.position + (SystemClock.elapsedRealtime() - state.lastPositionUpdateTime) * state.playbackSpeed
            return (pos / duration).coerceIn(0f, 1f)
        }
        return null
    }

    private fun update() {
        val p = prefs
        val now = SystemClock.uptimeMillis()
        var ticking = true // media en demo lopen vanzelf door: dan regelmatig bijwerken
        val target: Float? = when {
            now < demoUntil -> (now - demoStart) / 4000f
            !p.lineOn -> null
            p.lineDownloads && downloads.isNotEmpty() -> downloads.values.last().also { ticking = false }
            p.lineMedia -> mediaProgress()
            else -> null
        }
        handler.removeCallbacks(poll)
        if (target != null && ticking) handler.postDelayed(poll, if (now < demoUntil) 50 else 500)
        if (now in demoStart..demoUntil + 600 && target == null) { handler.postDelayed(poll, 100); return }
        if (target == null) hideLine() else showLine().setProgress(target.coerceAtMost(1f).let { if (it < 0) -1f else it })
    }

    private fun showLine(): LineView {
        line?.let { return it }
        val p = prefs
        val color = when (p.lineColor) {
            1 -> getColor(android.R.color.system_accent1_300)
            2 -> android.graphics.Color.WHITE
            else -> p.accent
        }
        val view = LineView(this, color)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            (p.lineThickness * resources.displayMetrics.density).toInt().coerceAtLeast(1),
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = if (p.lineBottom) Gravity.BOTTOM else Gravity.TOP
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            fitInsetsTypes = 0
            title = "Tint voortgangslijn"
        }
        return try {
            getSystemService(WindowManager::class.java).addView(view, lp)
            line = view
            view
        } catch (e: Exception) {
            // Geen toestemming om over andere apps te tekenen: dan stil niets tonen
            view
        }
    }

    private fun hideLine() {
        line?.let { runCatching { getSystemService(WindowManager::class.java).removeView(it) } }
        line = null
    }
}

/** Het lijntje zelf: vloeiend naar de nieuwe waarde, of een heen-en-weer schuivend stukje als de voortgang onbekend is. */
class LineView(context: Context, lineColor: Int) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = lineColor }
    private val track = Paint().apply { color = withAlpha(lineColor, 0.18f) }
    private var target = 0f
    private var shown = 0f

    fun setProgress(p: Float) {
        target = p
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        canvas.drawRect(0f, 0f, w, h, track)
        if (target < 0) {
            val t = (SystemClock.uptimeMillis() % 1600) / 1600f
            val seg = w * 0.3f
            val x = -seg + (w + seg) * t
            canvas.drawRect(x, 0f, x + seg, h, paint)
            postInvalidateOnAnimation()
            return
        }
        shown += (target - shown) * 0.2f
        if (abs(target - shown) < 0.001f) shown = target else postInvalidateOnAnimation()
        canvas.drawRect(0f, 0f, w * shown, h, paint)
    }
}

