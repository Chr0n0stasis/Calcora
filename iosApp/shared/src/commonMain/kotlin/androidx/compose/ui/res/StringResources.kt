package androidx.compose.ui.res

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.StringResource

@Composable
fun stringResource(id: StringResource): String =
    org.jetbrains.compose.resources.stringResource(id)

@Composable
fun stringResource(id: StringResource, vararg formatArgs: Any): String =
    org.jetbrains.compose.resources.stringResource(id, *formatArgs)
