package mo.dev.ctrus.util

/** Port of DomainPicker.swift's isValidDomain — same regex and extra checks. */
object DomainValidator {
    private const val MAX_LENGTH = 253
    private val DOMAIN_REGEX = Regex(
        "^[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?(\\.[a-zA-Z0-9]([a-zA-Z0-9-]{0,61}[a-zA-Z0-9])?)*$",
    )

    fun isValid(domain: String): Boolean {
        if (domain.length > MAX_LENGTH) return false
        if (domain.startsWith(".") || domain.endsWith(".")) return false
        if (domain.contains("..")) return false
        if (!domain.contains(".")) return false
        return DOMAIN_REGEX.matches(domain)
    }
}
