package mo.dev.ctrus.util

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * A string resource plus optional format args, carried through non-Composable layers (strategy
 * results, the session ViewModel's error state) that must stay Context-free — resolved to actual
 * localized text only at the Compose call site via [resolve].
 */
data class UiText(@StringRes val resId: Int, val args: List<Any> = emptyList()) {
    constructor(@StringRes resId: Int, vararg args: Any) : this(resId, args.toList())
}

@Composable
fun UiText.resolve(): String =
    if (args.isEmpty()) stringResource(resId) else stringResource(resId, *args.toTypedArray())
