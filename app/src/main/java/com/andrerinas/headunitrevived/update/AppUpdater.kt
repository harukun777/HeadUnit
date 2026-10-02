package com.andrerinas.headunitrevived.update

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.pm.PackageInfoCompat
import com.andrerinas.headunitrevived.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

internal object AppUpdater {
    private val mutex = Mutex()
    private const val MAX_APK = 150L * 1024 * 1024
    private const val INTERVAL = 24L * 60 * 60 * 1000
    fun prefs(context: Context) = context.getSharedPreferences("app-updates", Context.MODE_PRIVATE)
    fun apk(context: Context) = File(context.cacheDir, "updates/update.apk")

    // Each redirect must retain HTTPS; APK signature verification also pins the installed app.
    private fun open(url: String): HttpURLConnection {
        var location = url
        repeat(8) {
            UpdatePolicy.requireHttps(location)
            val connection = URL(location).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15000
            connection.readTimeout = 20000
            connection.setRequestProperty("User-Agent", "Auto on Andoroid/${BuildConfig.VERSION_NAME}")
            val status = connection.responseCode
            if (status in listOf(301, 302, 303, 307, 308)) {
                val target = connection.getHeaderField("Location")
                connection.disconnect()
                require(!target.isNullOrBlank()) { "Missing redirect location" }
                location = URL(URL(location), target).toString()
            } else {
                if (status != 200) {
                    connection.disconnect()
                    error("HTTP $status")
                }
                return connection
            }
        }
        error("Too many redirects")
    }

    suspend fun check(context: Context, automatic: Boolean): File? = withContext(Dispatchers.IO) {
        mutex.withLock {
            val prefs = prefs(context)
            val feed = prefs.getString("url", "")!!.trim()
            if (feed.isEmpty()) return@withLock null
            if (automatic && !prefs.getBoolean("enabled", true)) return@withLock null
            val now = System.currentTimeMillis()
            val last = prefs.getLong("last-check", 0)
            if (automatic && now >= last && now - last < INTERVAL) return@withLock null
            // Throttle failed automatic requests too. Manual checks always bypass this.
            prefs.edit().putLong("last-check", now).apply()
            val connection = open(feed)
            val metadata = try {
                connection.inputStream.use { input ->
                    val bytes = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(4096)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        require(bytes.size() + count <= 65536) { "Update manifest too large" }
                        bytes.write(buffer, 0, count)
                    }
                    JSONObject(bytes.toString("UTF-8"))
                }
            } finally { connection.disconnect() }
            val version = metadata.getLong("versionCode")
            require(metadata.getString("packageName") == context.packageName) { "Wrong application" }
            if (!UpdatePolicy.eligible(version, BuildConfig.VERSION_CODE.toLong(), metadata.getInt("minSdk"),
                    android.os.Build.VERSION.SDK_INT, metadata.getString("variant"), BuildConfig.FLAVOR)) return@withLock null
            val expectedHash = metadata.getString("sha256").lowercase()
            require(expectedHash.matches(Regex("[0-9a-f]{64}"))) { "Invalid SHA-256" }
            val target = apk(context)
            if (target.exists() && hash(target) == expectedHash) {
                verify(context, target, version)
                return@withLock target
            }
            target.parentFile!!.mkdirs()
            val partial = File(target.parentFile, "update.part")
            try {
                val download = open(metadata.getString("apkUrl"))
                try {
                    download.inputStream.use { input ->
                        partial.outputStream().use { output ->
                            val buffer = ByteArray(32768)
                            var total = 0L
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                total += count
                                require(total <= MAX_APK) { "APK too large" }
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                } finally { download.disconnect() }
                require(hash(partial) == expectedHash) { "APK hash mismatch" }
                verify(context, partial, version)
                if (target.exists()) require(target.delete()) { "Cannot replace previous APK" }
                require(partial.renameTo(target)) { "Cannot save APK" }
                target
            } finally { partial.delete() }
        }
    }

    private fun hash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(32768)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
    }

    @Suppress("DEPRECATION")
    fun verify(context: Context, file: File, expectedVersion: Long? = null) {
        val pm = context.packageManager
        val candidate = pm.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNATURES)
            ?: error("Invalid APK")
        val installed = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
        val version = PackageInfoCompat.getLongVersionCode(candidate)
        require(candidate.packageName == context.packageName && version > PackageInfoCompat.getLongVersionCode(installed)) {
            "APK is not a newer version of this application"
        }
        require(expectedVersion == null || version == expectedVersion) { "APK version mismatch" }
        require(UpdatePolicy.matchingSigners(installed.signatures.orEmpty().map { it.toCharsString() }.toSet(),
            candidate.signatures.orEmpty().map { it.toCharsString() }.toSet())) { "APK signing certificate mismatch" }
        if (android.os.Build.VERSION.SDK_INT >= 24) {
            require((candidate.applicationInfo?.minSdkVersion ?: Int.MAX_VALUE) <= android.os.Build.VERSION.SDK_INT) {
                "APK requires a newer Android version"
            }
        }
    }
}
