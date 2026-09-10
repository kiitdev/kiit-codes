/** url: www.kiit.dev */
@file:OptIn(ExperimentalJsExport::class)

package kiit.codes.formats

import kiit.codes.StatusConstants
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * Maps a [kiit.codes.Status.origin] to the `baseUrl` [CodesToProblem] should use for it,
 * registered once instead of passed at every call site. [StatusConstants.KIIT] is pre-registered;
 * every other origin needs a [register] call before [CodesToProblem] can convert it.
 */
@JsExport
class Catalog {
    private val baseUrls = mutableMapOf(StatusConstants.KIIT to "https://kiit.dev/problems")

    /** Registers [baseUrl] as where [origin]'s problem types are documented. */
    fun register(origin: String, baseUrl: String) {
        baseUrls[origin] = baseUrl
    }

    /** The registered `baseUrl` for [origin], or null if nothing's been registered. */
    fun baseUrlFor(origin: String): String? = baseUrls[origin]
}
