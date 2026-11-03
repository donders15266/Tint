package nl.jesse.tint

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Updates over the air: GitHub Actions bouwt bij elke wijziging op main een release met tag
 * `v<versionCode>` en een bijgevoegde Tint.apk. Hier kijken we of die nieuwer is en installeren
 * we hem via de PackageInstaller (Android vraagt altijd zelf om bevestiging).
 */
object Updater {
    /** Repository op GitHub, als "eigenaar/naam". Wordt bij het bouwen ingevuld. */
    val REPO: String get() = BuildConfig.UPDATE_REPO

    data class Release(val versionCode: Long, val name: String, val notes: String, val apkUrl: String)

    fun currentVersion(context: Context): Long =
        context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode

    /**
     * Haalt de nieuwste release op. Niet op de hoofdthread aanroepen.
     * De repository is privé: daarom gaat er een alleen-lezen token mee.
     */
    fun latest(token: String): Release? {
        val conn = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        if (token.isNotBlank()) conn.setRequestProperty("Authorization", "Bearer ${token.trim()}")
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        try {
            if (conn.responseCode == 401) error("token ongeldig of verlopen")
            if (conn.responseCode == 404) return null
            if (conn.responseCode !in 200..299) error("GitHub antwoordde met ${conn.responseCode}")
            val json = JSONObject(conn.inputStream.bufferedReader().readText())
            val code = json.getString("tag_name").removePrefix("v").toLongOrNull() ?: return null
            val assets = json.getJSONArray("assets")
            var url: String? = null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                // De API-link van het bestand werkt ook voor privé-repositories
                if (a.getString("name").endsWith(".apk")) { url = a.getString("url"); break }
            }
            return Release(code, json.optString("name", "v$code"), json.optString("body", ""), url ?: return null)
        } finally {
            conn.disconnect()
        }
    }

    /** Downloadt de APK naar de cache en geeft het bestand terug. [progress] krijgt 0..1. */
    fun download(context: Context, release: Release, token: String, progress: (Float) -> Unit): File {
        val file = File(context.cacheDir, "update.apk")
        var url = URL(release.apkUrl)
        // GitHub stuurt door naar een downloadserver: volg dat zelf, ook naar een ander domein
        repeat(5) {
            val conn = url.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            // Alleen GitHub zelf krijgt het token; de downloadserver krijgt een tijdelijke link
            if (url.host == "api.github.com") {
                conn.setRequestProperty("Accept", "application/octet-stream")
                if (token.isNotBlank()) conn.setRequestProperty("Authorization", "Bearer ${token.trim()}")
            }
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            val code = conn.responseCode
            if (code in 300..399) {
                url = URL(url, conn.getHeaderField("Location"))
                conn.disconnect()
                return@repeat
            }
            if (code !in 200..299) error("Download mislukt (${code})")
            val total = conn.contentLengthLong
            conn.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0) progress(done.toFloat() / total)
                    }
                }
            }
            conn.disconnect()
            return file
        }
        error("Te veel doorverwijzingen")
    }

    /** Biedt de APK aan Android aan; die toont het gewone installatiescherm. */
    fun install(context: Context, apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("tint.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val intent = Intent(context, InstallReceiver::class.java)
            val pi = PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            session.commit(pi.intentSender)
        }
    }
}

/** Ontvangt de uitkomst van de installatie en opent zo nodig het bevestigingsscherm van Android. */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            PackageInstaller.STATUS_SUCCESS -> Unit // de app wordt vervangen en opnieuw gestart
            else -> {
                val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "onbekende fout"
                Toast.makeText(context, "Bijwerken mislukt: $msg", Toast.LENGTH_LONG).show()
            }
        }
    }
}
