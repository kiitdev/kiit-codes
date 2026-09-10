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
 * [StatusConstants.KIIT] is always present, defaulting to `https://kiit.dev/problems` unless
 * overridden in [of]'s [baseUrls].
 */
@JsExport
class Catalog private constructor(private val baseUrls: Map<String, String>) {
    /** The registered `baseUrl` for [origin], or null if none was passed to [of]. */
    fun baseUrlFor(origin: String): String? = baseUrls[origin]

    companion object {
        private const val DEFAULT_KIIT_BASE_URL = "https://kiit.dev/problems"

        /** Builds a [Catalog] from [baseUrls], upserting [StatusConstants.KIIT]'s default entry. */
        @JvmStatic
        @JvmOverloads
        fun of(baseUrls: Map<String, String> = emptyMap()): Catalog =
            Catalog(mapOf(StatusConstants.KIIT to DEFAULT_KIIT_BASE_URL) + baseUrls)
    }
}
