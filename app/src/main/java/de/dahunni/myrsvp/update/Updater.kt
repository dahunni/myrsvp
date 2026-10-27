package de.dahunni.myrsvp.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

enum class UpdateError { Offline, Download, Checksum }

class UpdateException(val error: UpdateError, cause: Throwable? = null) : Exception(error.name, cause)

/** What happened when the APK was handed to Android. */
enum class InstallStart { Started, NeedsPermission }

/**
 * The app's only network access: asks GitHub for the latest release of [repository] and
 * downloads its APK. Installing is left to Android's package installer, which only accepts an
 * APK signed with the same key as the installed app.
 */
class Updater(private val context: Context, private val repository: String, private val userAgent: String) {
    /** The latest published release, or null if the repository has none. */
    suspend fun latest(): Release? = withContext(Dispatchers.IO) {
        val connection = open("https://api.github.com/repos/$repository/releases/latest").apply {
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        }
        try {
            when (connection.responseCode) {
                HttpURLConnection.HTTP_OK -> Releases.parse(connection.inputStream.bufferedReader().use { it.readText() })
                HttpURLConnection.HTTP_NOT_FOUND -> null
                else -> throw UpdateException(UpdateError.Offline)
            }
        } catch (e: IOException) {
            throw UpdateException(UpdateError.Offline, e)
        } finally {
            connection.disconnect()
        }
    }

    /** Downloads the release's APK into the cache and checks its SHA-256 if GitHub published one. */
    suspend fun download(release: Release, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val url = release.apkUrl ?: throw UpdateException(UpdateError.Download)
        val dir = File(context.cacheDir, UPDATE_DIR).apply { deleteRecursively(); mkdirs() }
        val file = File(dir, "myrsvp-${release.version}.apk")
        val digest = MessageDigest.getInstance("SHA-256")
        val connection = open(url)
        try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) throw UpdateException(UpdateError.Download)
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: release.apkSize
            connection.inputStream.use { input ->
                file.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var done = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        digest.update(buffer, 0, n)
                        done += n
                        if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        } catch (e: IOException) {
            throw UpdateException(UpdateError.Download, e)
        } finally {
            connection.disconnect()
        }
        val expected = release.apkSha256
        if (expected != null && !digest.digest().toHex().equals(expected, ignoreCase = true)) {
            file.delete()
            throw UpdateException(UpdateError.Checksum)
        }
        file
    }

    /**
     * Opens Android's installer for [apk]. The first time, Android asks the user to allow
     * installs from this app; then the user comes back and installs again.
     */
    fun install(apk: File): InstallStart {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return InstallStart.NeedsPermission
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        return InstallStart.Started
    }

    private fun open(url: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = TIMEOUT_MS
        readTimeout = TIMEOUT_MS
        setRequestProperty("User-Agent", userAgent)
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

    companion object {
        /** Folder in the cache dir; shared with the installer through res/xml/update_paths.xml. */
        const val UPDATE_DIR = "updates"
        private const val TIMEOUT_MS = 15_000
    }
}
