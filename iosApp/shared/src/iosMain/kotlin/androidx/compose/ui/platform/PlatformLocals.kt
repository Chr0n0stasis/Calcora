@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package androidx.compose.ui.platform

import android.content.Intent
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import java.io.File
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIApplication
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle

class IOSResources {
    fun getFont(@Suppress("UNUSED_PARAMETER") resource: Int): Typeface =
        Typeface.create("Menlo", Typeface.NORMAL)
}

class IOSContext internal constructor() {
    val resources = IOSResources()
    val filesDir: File by lazy {
        val path = NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory, NSUserDomainMask, true
        ).firstOrNull() as? String ?: "."
        File(path)
    }

    fun startActivity(intent: Intent) {
        intent.uri.url?.let { UIApplication.sharedApplication.openURL(it) }
    }
}

object LocalContext {
    private val value = IOSContext()
    val current: IOSContext @Composable get() = value
}

class IOSView {
    fun performHapticFeedback(kind: Int): Boolean {
        val style = if (kind == android.view.HapticFeedbackConstants.LONG_PRESS)
            UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium else UIImpactFeedbackStyle.UIImpactFeedbackStyleLight
        UIImpactFeedbackGenerator(style).apply { prepare(); impactOccurred() }
        return true
    }
}

object LocalView {
    private val value = IOSView()
    val current: IOSView @Composable get() = value
}
