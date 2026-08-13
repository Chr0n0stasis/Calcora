package android.content

import android.net.Uri

class Intent(val action: String, val uri: Uri) {
    companion object { const val ACTION_VIEW = "android.intent.action.VIEW" }
}
