package mo.dev.ctrus.strategy

/**
 * Mirrors FocusMessages.swift: picks one of the 16 rotating focus messages shown on the active
 * session screen. Kept as an index (not a string) so this stays Context-free — the actual
 * localized text lives in R.array.focus_messages, resolved at the Compose call site.
 */
object FocusMessages {
    const val COUNT = 16

    fun randomIndex(): Int = (0 until COUNT).random()
}
