/** url: www.kiit.dev */
@file:OptIn(ExperimentalJsExport::class)

package kiit.codes.formats

import kiit.codes.StatusConstants
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * Immutable map of [kiit.codes.Status.origin] to the `baseUrl` [CodesToProblem] should use for
 * it. Build one via [Catalog.of].
 *
 * [StatusConstants.KIIT] is always present and always resolves to kiit-codes' own taxonomy
 * docs — [of]'s [baseUrls] cannot override it, kiit-origin statuses always point back here.
 */
@JsExport
class Catalog private constructor(private val baseUrls: Map<String, String>) {
    /** The registered `baseUrl` for [origin], or null if none was passed to [of]. */
    fun baseUrlFor(origin: String): String? = baseUrls[origin]

    companion object {
        private const val DEFAULT_KIIT_BASE_URL = "https://www.kiit.dev/docs/kiit-codes"

        /** Builds a [Catalog] from [baseUrls], with [StatusConstants.KIIT] fixed to its own docs. */
        @JvmStatic
        @JvmOverloads
        fun of(baseUrls: Map<String, String> = emptyMap()): Catalog {
            return Catalog(baseUrls + (StatusConstants.KIIT to DEFAULT_KIIT_BASE_URL))
        }
    }
}
