package kiit.codes

import kotlin.jvm.JvmStatic

/**
 * The rules for a [Status]'s `origin`, `scope` and `name`. They are joined with `:` into [code] and with `/` into the
 * RFC 9457 `type`, so a stray delimiter would silently change both. Every [Passed] and [Failed] subtype checks them
 * when it is constructed (or copied) and throws [IllegalArgumentException] for a bad value.
 *
 * 1. Allowed characters are ASCII letters, digits, `.`, `-` and `_`. Case is not enforced and there is no length limit.
 * 2. A value that is not empty starts with a letter or digit, so `.` and `..` can't become a path segment.
 * 3. `origin` and `name` must not be empty. `scope` may be empty, which means unset.
 * 4. `kiit.dev` is reserved for kiit-authored codes. This is a convention and is not checked.
 *
 * Values are constants in source, so a bad one is a programmer error and throws. For a value that comes from config or
 * other runtime input, call [check] first, it never throws:
 *
 * ```kotlin
 * val error: String? = StatusRules.check(origin = cfg.origin, scope = cfg.scope, name = "PAYMENT_DECLINED")
 * if (error == null) Rejected("PAYMENT_DECLINED", "Declined", origin = cfg.origin, scope = cfg.scope)
 * ```
 */
object StatusRules {
    /**
     * How many characters of a bad value an error message shows, the rest is replaced by `...`. It is not a limit on
     * the field, which has none. It only keeps a long value from runtime input out of exception messages and logs. The
     * cut can hide the bad character, the reason text still names the allowed ones.
     */
    private const val MAX_SHOWN = 32

    /**
     * Checks the three values and returns the first problem as a message, or null when all are valid.
     * The message names the field and shows the value with non-printable characters escaped and long values cut.
     */
    @JvmStatic
    fun check(origin: String, scope: String, name: String): String? =
        checkField("origin", origin, allowEmpty = false)
            ?: checkField("scope", scope, allowEmpty = true)
            ?: checkField("name", name, allowEmpty = false)

    /** Throws [IllegalArgumentException] with the message from [check], if there is one. */
    internal fun validate(origin: String, scope: String, name: String) {
        val error = check(origin, scope, name)
        require(error == null) { error.orEmpty() }
    }

    private fun checkField(field: String, value: String, allowEmpty: Boolean): String? {
        if (value.isEmpty()) return if (allowEmpty) null else "$field must not be empty"
        if (!isAlphaNumeric(value[0])) return invalid(field, value, "must start with a letter or digit")
        for (c in value) {
            if (!isAllowed(c)) {
                return invalid(field, value, "allowed characters are a-z, A-Z, 0-9, '.', '-', '_'")
            }
        }
        return null
    }

    private fun isAlphaNumeric(c: Char): Boolean = c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9'

    private fun isAllowed(c: Char): Boolean = isAlphaNumeric(c) || c == '.' || c == '-' || c == '_'

    private fun invalid(field: String, value: String, reason: String): String {
        return "$field \"${shown(value)}\" is invalid: $reason"
    }

    private fun shown(value: String): String {
        val head = if (value.length > MAX_SHOWN) value.substring(0, MAX_SHOWN) else value
        val escaped =
            buildString {
                for (c in head) {
                    if (c in ' '..'~') append(c) else append("\\u").append(c.code.toString(16).padStart(4, '0'))
                }
            }
        return if (value.length > MAX_SHOWN) "$escaped..." else escaped
    }
}
