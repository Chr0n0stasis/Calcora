package androidx.activity.compose

import androidx.compose.runtime.Composable

/** iOS screens expose their own visible back actions; the system handler is Android-only. */
@Composable
fun BackHandler(
    @Suppress("UNUSED_PARAMETER") enabled: Boolean = true,
    @Suppress("UNUSED_PARAMETER") onBack: () -> Unit
) = Unit
