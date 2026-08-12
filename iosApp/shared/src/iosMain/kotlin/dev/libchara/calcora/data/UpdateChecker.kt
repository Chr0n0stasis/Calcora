@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.libchara.calcora.data

import dev.libchara.calcora.BuildConfig
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.get
import kotlinx.cinterop.reinterpret
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import platform.Foundation.NSData
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.setValue
import kotlin.coroutines.resume

data class ReleaseInfo(val version: String, val pageUrl: String)
sealed interface UpdateCheckResult {
    data class UpdateAvailable(val release: ReleaseInfo) : UpdateCheckResult
    data class UpToDate(val release: ReleaseInfo) : UpdateCheckResult
    data class Failed(val message: String) : UpdateCheckResult
}

object UpdateChecker {
    suspend fun checkLatestRelease(): UpdateCheckResult = runCatching {
        val url = NSURL.URLWithString("https://api.github.com/repos/${BuildConfig.GITHUB_REPO}/releases/latest")
            ?: error("Invalid release URL")
        val request = NSMutableURLRequest.requestWithURL(url).apply {
            setValue("application/vnd.github+json", forHTTPHeaderField = "Accept")
            setValue("Calcora/${BuildConfig.VERSION_NAME}", forHTTPHeaderField = "User-Agent")
        }
        val text = suspendCancellableCoroutine<String> { continuation ->
            val task = NSURLSession.sharedSession.dataTaskWithRequest(request) { data, _, error ->
                if (error != null || data == null) continuation.resume("")
                else continuation.resume(data.utf8String())
            }
            continuation.invokeOnCancellation { task.cancel() }
            task.resume()
        }
        if (text.isBlank()) error("Network error")
        val json = JSONObject(text)
        val release = ReleaseInfo(
            normalizeVersion(json.optString("tag_name").ifBlank { json.optString("name") }),
            json.optString("html_url", "https://github.com/${BuildConfig.GITHUB_REPO}/releases")
        )
        if (isRemoteNewer(release.version, BuildConfig.VERSION_NAME))
            UpdateCheckResult.UpdateAvailable(release) else UpdateCheckResult.UpToDate(release)
    }.getOrElse { UpdateCheckResult.Failed(it.message ?: "Network error") }

    private fun NSData.utf8String(): String {
        val pointer = bytes?.reinterpret<ByteVar>() ?: return ""
        return ByteArray(length.toInt()) { pointer[it] }.decodeToString()
    }

    private fun normalizeVersion(value: String) = value.trim().removePrefix("v").removePrefix("V")
    private fun isRemoteNewer(remote: String, local: String): Boolean {
        val r = normalizeVersion(remote).split('.', '-', '_').mapNotNull { it.toIntOrNull() }
        val l = normalizeVersion(local).split('.', '-', '_').mapNotNull { it.toIntOrNull() }
        for (index in 0 until maxOf(r.size, l.size)) {
            val difference = r.getOrElse(index) { 0 } - l.getOrElse(index) { 0 }
            if (difference != 0) return difference > 0
        }
        return false
    }
}
