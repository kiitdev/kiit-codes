/** url: www.kiit.dev */
@file:OptIn(ExperimentalJsExport::class)

package kiit.codes.formats

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kiit.codes.*

/**
 * kiit-side name for RFC 9457's "problem details object"
 * (https://www.rfc-editor.org/rfc/rfc9457.html). Opt-in output shape, never returned by default
 * from anything. Build one explicitly via [toProblemDetail].
 *
 * Shape:
 * ```json
 * {
 *      "type"   : "https://stripe.com/problems/payments.cards/rejected/duplicate-charge",
 *      "title"  : "This charge has already been processed",
 *      "status" : 409
 * }
 * ```
 *
 * For internal service-to-service calls, background jobs, or anywhere else an HTTP status code
 * and a URI `type` don't mean anything, see [CodeDetail]. It's kiit-codes' own native
 * equivalent, built from [Status.path]/[Status.code] instead of RFC 9457's vocabulary.
 */
@JsExport
data class Problem(
    val type: String,
    val title: String,
    val status: Int,
    val detail: String? = null,
    val instance: String? = null,
    val errors: List<ErrorDetail>? = null,
)
