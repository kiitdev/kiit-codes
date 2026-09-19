/** url: www.kiit.dev */
package kiit.codes.formats

import kiit.codes.StatusConstants
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * Optional mapping of [kiit.codes.Status.origin] to a `baseUrl`, used by [CodesToProblem] to build
 * the `type` of an [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457.html) problem. Build one via [of].
 *
 *
 * TERMS
 * 1. origin: [kiit.codes.Status.origin], the key of each entry, e.g. `"stripe.com"`.
 * 2. baseUrl: text before the suffix, path prefix included, no trailing slash. e.g. `"https://stripe.com/errors"`
 * 3. suffix: `{scope}/{group}/{name}` in lowercase-dash, empty scope skipped. e.g. `"payments.cards/rejected/duplicate-charge"`
 * 4. type: the RFC 9457 `type` field, `{baseUrl}/{suffix}`.
 *
 *
 * OPTIONAL
 * 1. Registration is optional.
 * 2. Without an entry, [CodesToProblem] uses `https://{origin}/problems` as the baseUrl.
 * 3. Register an origin when its problem docs live elsewhere, or when it isn't a domain.
 * 4. An entry here wins over the origin-derived baseUrl.
 *
 *
 * EXAMPLE
 * 1. origin `"stripe.com"` -> baseUrl `"https://stripe.com/errors"`
 * 2. origin `"myapp1"` -> baseUrl `"https://docs.example.com/myapp1/errors"`
 *
 * With the first entry, a `Rejected` status named `DUPLICATE_CHARGE` with scope `payments.cards`
 * builds this:
 * ```json
 * {
 *     "type": "https://stripe.com/errors/payments.cards/rejected/duplicate-charge",
 *     "title": "This charge has already been processed",
 *     "status": 409
 * }
 * ```
 *
 *
 * NOTES
 * 1. Read an entry with [baseUrlFor].
 * 2. See [CodesToProblem] for how `type` is built and [Problem] for RFC 9457.
 * 3. [StatusConstants.KIIT] is always present and resolves to kiit-codes' own taxonomy docs.
 * 4. [of] can't override it. Its suffix is `?code=...#taxonomy`, joined with no `/`.
 */
class Catalog private constructor(private val baseUrls: Map<String, String>) {
    /** The registered `baseUrl` for [origin], or null if none was passed to [of]. */
    fun baseUrlFor(origin: String): String? = baseUrls[origin]

    companion object {
        internal const val KIIT_BASE_URL = "https://www.kiit.dev/docs/kiit-codes"

        /** Builds a [Catalog] from [baseUrls], with [StatusConstants.KIIT] fixed to its own docs. */
        @JvmStatic
        @JvmOverloads
        fun of(baseUrls: Map<String, String> = emptyMap()): Catalog {
            return Catalog(baseUrls + (StatusConstants.KIIT to KIIT_BASE_URL))
        }
    }
}
