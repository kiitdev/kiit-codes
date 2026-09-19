/** url: www.kiit.dev */
package kiit.codes.formats

import kiit.codes.CodesToHttp
import kiit.codes.Err
import kiit.codes.Status
import kiit.codes.StatusConstants
import kiit.codes.code
import kotlin.jvm.JvmOverloads

private const val KIIT_BASE_URL = "https://www.kiit.dev/docs/kiit-codes"

/**
 * Converts a [Status] into an [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457.html) [Problem], the way
 * [kiit.codes.CodesToHttp]/[kiit.codes.CodesToGrpc] convert one into a protocol code. [baseUrls] is an
 * optional mapping of [Status.origin] to a `baseUrl`; [mapping] is reused rather than a fresh [CodesToHttp] per call.
 *
 *
 * TERMS
 * 1. origin: [Status.origin], the key of each [baseUrls] entry, e.g. `"stripe.com"`.
 * 2. baseUrl: text before the suffix, path prefix included, no trailing slash. e.g. `"https://stripe.com/errors"`
 * 3. suffix: `{scope}/{group}/{name}` in lowercase-dash, empty scope skipped.
 *    e.g. `"payments.cards/rejected/duplicate-charge"`
 * 4. type: the RFC 9457 `type` field, `{baseUrl}/{suffix}`.
 *
 *
 * OPTIONAL
 * 1. [baseUrls] is optional.
 * 2. Without an entry, `https://{origin}/problems` is the baseUrl. The origin is lowercased and not validated.
 * 3. Add an entry when an origin's problem docs live elsewhere, or when it isn't a domain.
 * 4. An entry wins over the origin-derived baseUrl.
 *
 *
 * EXAMPLE
 * 1. origin `"stripe.com"` -> baseUrl `"https://stripe.com/errors"`
 * 2. origin `"myapp1"` -> baseUrl `"https://docs.example.com/myapp1/errors"`
 *
 * ```kotlin
 * val problems = ProblemConverter(baseUrls = mapOf("stripe.com" to "https://stripe.com/errors"))
 * ```
 *
 * With the first entry, a `Rejected` status named `DUPLICATE_CHARGE` with scope `payments.cards` builds this:
 * ```json
 * {
 *     "type": "https://stripe.com/errors/payments.cards/rejected/duplicate-charge",
 *     "title": "This charge has already been processed",
 *     "status": 409
 * }
 * ```
 *
 * With no entry, the same status with origin `"stripe.com"` builds the `type`
 * `https://stripe.com/problems/payments.cards/rejected/duplicate-charge`.
 *
 *
 * NOTES
 * 1. Keys of [baseUrls] are lowercased and a trailing `/` on a value is trimmed.
 * 2. [StatusConstants.KIIT] is always present and resolves to kiit-codes' own taxonomy docs.
 * 3. [StatusConstants.KIIT] can't be overridden. Its suffix is `?code=...#taxonomy`, joined with no `/`.
 * 4. See [defaultTypeBuilder] for the suffix, and [Problem] for the RFC 9457 shape.
 */
class ProblemConverter
    @JvmOverloads
    constructor(
        baseUrls: Map<String, String> = emptyMap(),
        private val mapping: CodesToHttp = CodesToHttp(),
    ) {
        private val registered: Map<String, String> =
            baseUrls.entries.associate { (origin, baseUrl) -> origin.lowercase() to baseUrl.trimEnd('/') } +
                (StatusConstants.KIIT to KIIT_BASE_URL)

        /** Builds a [Problem]\<[ErrorDetail]\> for [status], baseUrl from [baseUrls] or the origin. */
        @Suppress("ktlint:standard:function-signature")
        @JvmOverloads
        fun convert(
            status: Status,
            err: Err? = null,
            typeBuilder: (Status) -> String = ::defaultTypeBuilder,
        ): Problem<ErrorDetail> {
            return convertCustom(status, err, typeBuilder, ::defaultErrorItem)
        }

        /**
         * Builds a [Problem]\<[T]\>, mapping each [Err.ErrorList] entry through [mapper]. Use this for
         * anything richer than field + message, see [ErrorItem]. Named differently from [convert]
         * (not an overload) since both take a trailing function parameter — Kotlin can't tell a
         * `convert(status, err) { ... }` call apart from one meant for [convert]'s own `typeBuilder`.
         *
         * [mapper] comes last (after the defaulted [typeBuilder]) so it can be passed as a trailing
         * lambda — Kotlin's trailing-lambda syntax always binds to a function's last parameter.
         */
        fun <T : ErrorItem> convertCustom(
            status: Status,
            err: Err?,
            typeBuilder: (Status) -> String = ::defaultTypeBuilder,
            mapper: (Err) -> T,
        ): Problem<T> {
            return convertCustomWithUrl(status, err, baseUrlFor(status), typeBuilder, mapper)
        }

        /** Same as [convert], but [baseUrl] is supplied directly instead of looked up from [baseUrls]. */
        @JvmOverloads
        fun convertWithUrl(
            status: Status,
            err: Err? = null,
            baseUrl: String,
            typeBuilder: (Status) -> String = ::defaultTypeBuilder,
        ): Problem<ErrorDetail> {
            return convertCustomWithUrl(status, err, baseUrl, typeBuilder, ::defaultErrorItem)
        }

        /**
         * Generic form of [convertWithUrl], mapping each [Err.ErrorList] entry through [mapper]. This is the one
         * function that assembles the [Problem], every other `convert*` function calls it.
         */
        fun <T : ErrorItem> convertCustomWithUrl(
            status: Status,
            err: Err?,
            baseUrl: String,
            typeBuilder: (Status) -> String = ::defaultTypeBuilder,
            mapper: (Err) -> T,
        ): Problem<T> {
            val base = baseUrl.trimEnd('/')
            val path = typeBuilder(status)
            val type =
                when {
                    path.isEmpty() -> base
                    path.startsWith("?") || path.startsWith("#") -> "$base$path"
                    else -> "$base/$path"
                }
            val code = mapping.toCode(status)
            return when (err) {
                is Err.ErrorList ->
                    Problem(
                        type = type,
                        title = status.message,
                        status = code,
                        detail = err.message,
                        errors = err.errors.map(mapper),
                    )
                else ->
                    Problem(
                        type = type,
                        title = status.message,
                        status = code,
                        detail = err?.message,
                        instance = err?.ref?.toString(),
                    )
            }
        }

        private fun baseUrlFor(status: Status): String {
            val origin = status.origin.lowercase()
            return registered[origin] ?: "https://$origin/problems"
        }
    }

/**
 * Default `type` suffix appended to the baseUrl: `scope`/`group`/`name`, lowercase-dash. `origin`
 * is left out on purpose, the baseUrl is already specific to one origin (see [ProblemConverter]), so
 * repeating it would just name that origin twice in the URL. An empty `scope` is skipped.
 *
 * ```kotlin
 * val status = Failed.Rejected("DUPLICATE_CHARGE", "Duplicate", origin = "stripe.com", scope = "payments.cards")
 * defaultTypeBuilder(status)   // "payments.cards/rejected/duplicate-charge"
 *
 * val noScope = Failed.Rejected("OUT_OF_STOCK", "Out of stock", origin = "myapp1")
 * defaultTypeBuilder(noScope)  // "rejected/out-of-stock"
 * ```
 *
 * [StatusConstants.KIIT] is the exception: kiit-codes' own docs don't have a per-code anchor yet,
 * just the taxonomy page as a whole, so every kiit-origin [Status] instead gets `status.code` as
 * a query param on that same page, e.g. `?code=Failed:Invalid:INVALID_VALUE#taxonomy`. A `code`
 * is a colon-delimited identifier (letters, digits, underscores), safe unencoded in a URI query.
 */
fun defaultTypeBuilder(status: Status): String {
    if (status.origin == StatusConstants.KIIT) return "?code=${status.code}#taxonomy"

    fun String.toUriSegment() = lowercase().replace("_", "-")
    val segments = listOfNotNull(status.scope.ifEmpty { null }, status.group, status.name)
    return segments.joinToString("/") { it.toUriSegment() }
}
