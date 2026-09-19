/** url: www.kiit.dev */
package kiit.codes.formats

import kiit.codes.StatusConstants
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * Container for optional mapping of [kiit.codes.Status.origin] to a `baseUrl.
 * This is used to support compliance with https://www.rfc-editor.org/rfc/rfc9457.html
 *
 *
 * OPTIONAL
 * 1. This registration of origin to a `baseUrl` is optional
 * 2. Register an origin when its problem docs live somewhere other than `https://{origin}/problems`
 * 3. Without an entry, [CodesToProblem] builds `type` from the [kiit.codes.Status.origin]
 *
 *
 * EXAMPLE
 * This is a sample of complying to RFC-9457 structure with a baseUrl
 * 1. origin: "stripe.com"  -> baseUrl: "https://stripe.com/problems-codes"
 * 2. origin: "kiit.dev"    -> baseUrl: "https://www.kiit.dev/docs/kiit-codes"
 *
 * ```json
 * {
 *     "type": "https://stripe.com/problems-codes/payments.cards/rejected/duplicate-charge",
 *     "title": "This charge has already been processed",
 *     "status": 409
 * }
 * ```
 *
 *
 * NOTES
 * 1. Refer to [Problem] for the implementation of RFC-9457 here
 * 2. Refer to [CodesToProblem] for how this is used
 * 3. This is only for lookup hould use this [kiit.codes.formats.Catalog] via [Catalog.baseUrlFor].
 * 4. Origin [StatusConstants.KIIT] is always present and always resolves to kiit-codes' own taxonomy docs
 *
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
