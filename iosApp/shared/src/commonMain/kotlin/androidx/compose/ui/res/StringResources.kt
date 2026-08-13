package androidx.compose.ui.res

import androidx.compose.runtime.Composable
import dev.libchara.calcora.R
import org.jetbrains.compose.resources.StringResource

@Composable
fun stringResource(id: StringResource): String =
    org.jetbrains.compose.resources.stringResource(id)

@Composable
fun stringResource(id: StringResource, vararg formatArgs: Any): String =
    org.jetbrains.compose.resources.stringResource(id, *formatArgs)

@Composable
fun stringResource(id: Int): String =
    org.jetbrains.compose.resources.stringResource(R.stringResource(id))

@Composable
fun stringResource(id: Int, vararg formatArgs: Any): String =
    org.jetbrains.compose.resources.stringResource(R.stringResource(id), *formatArgs)
