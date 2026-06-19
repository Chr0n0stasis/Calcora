package dev.libchara.calcora.data

import dev.libchara.calcora.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class ReleaseInfo(
    val version: String,
    val pageUrl: String
)

sealed interface UpdateCheckResult {
    data class UpdateAvailable(val release: ReleaseInfo) : UpdateCheckResult
    data class UpToDate(val release: ReleaseInfo) : UpdateCheckResult
    data class Failed(val message: String) : UpdateCheckResult
}

object UpdateChecker {
    private const val CONNECT_TIMEOUT_MS = 6_000
    private const val READ_TIMEOUT_MS = 8_000

    suspend fun checkLatestRelease(): UpdateCheckResult = withContext(Dispatchers.IO) {
        runCatching {
            val url = URL("https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest")
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "Calcora/${BuildConfig.VERSION_NAME}")
            }

            connection.inputStream.bufferedReader().use { reader ->
                val json = JSONObject(reader.readText())
                val release = ReleaseInfo(
                    version = normalizeVersion(json.optString("tag_name").ifBlank { json.optString("name") }),
                    pageUrl = json.optString("html_url", "https://github.com/${BuildConfig.GITHUB_REPO}/releases")
                )
                if (isRemoteNewer(release.version, BuildConfig.VERSION_NAME)) {
                    UpdateCheckResult.UpdateAvailable(release)
                } else {
                    UpdateCheckResult.UpToDate(release)
                }
            }
        }.getOrElse { error ->
            UpdateCheckResult.Failed(error.message ?: "Network error")
        }
    }

    private fun normalizeVersion(value: String): String =
        value.trim().removePrefix("v").removePrefix("V")

    private fun isRemoteNewer(remote: String, local: String): Boolean {
        val remoteParts = normalizeVersion(remote).split('.', '-', '_').mapNotNull { it.toIntOrNull() }
        val localParts = normalizeVersion(local).split('.', '-', '_').mapNotNull { it.toIntOrNull() }
        val size = maxOf(remoteParts.size, localParts.size)
        for (index in 0 until size) {
            val r = remoteParts.getOrElse(index) { 0 }
            val l = localParts.getOrElse(index) { 0 }
            if (r != l) return r > l
        }
        return false
    }
}
