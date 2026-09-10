/** url: www.kiit.dev */
@file:OptIn(ExperimentalJsExport::class)
@file:JvmName("CodeDetails")

package kiit.codes.formats

import kiit.codes.Err
import kiit.codes.Status
import kiit.codes.code
import kiit.codes.path
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads

/**
 * kiit-native counterpart to [Problem]
 * Opt-in alternative to RFC-9457 (below).
 * See : https://www.rfc-editor.org/rfc/rfc9457.html
 *
 * 1. No HTTP status code, meant for internal service to service calls
 * 2. Also, useful for background jobs, non-API calls where an HTTP status is not relevant
 * 3. Self-Contained without a need for a public URI. [Status.origin]:[Status.scope]
 *
 * @sample
 * ```json
 * {
 *      "path"    : "com.stripe:payments.cards",
 *      "code"    : "Failed:Rejected:DUPLICATE_CHARGE",
 *      "success" : false,
 *      "message" : "This charge has already been processed"
 * }
 * ```
 */
@JsExport
data class CodeDetail(
    val path: String,
    val code: String,
    val success: Boolean,
    val message: String,
    val detail: String? = null,
    val instance: String? = null,
    val errors: List<ErrorDetail>? = null,
) {
    companion object {

        /**
         * Builds a [CodeDetail] for [status] (and optionally [err]), kiit-codes' own equivalent of
         * [toProblemDetail] for callers with no use for an HTTP status code or a URI `type`. [status]
         * and [Status] itself are never modified.
         *
         * Field mapping:
         * - [Status.path]          : [CodeDetail.path]
         * - [Status.code]          : [CodeDetail.code]
         * - [Status.success]       : [CodeDetail.success], a quick success/fail check without pattern
         * - [Status.message]       : [CodeDetail.message].
         * - [Err.message]          : [CodeDetail.detail] (occurrence-specific, never on [status] itself).
         * - [Err.ref]              : [CodeDetail.instance]
         * - [Err.ErrorList.errors] : [CodeDetail.errors], any other [Err] shape leaves it null.
         *
         * [ProblemError] is reused here instead of duplicated. It's already kiit-codes' own name for one
         * wrapped [Err] entry (field + detail), not an RFC-specific shape.
         */
        @OptIn(ExperimentalJsExport::class)
        @JsExport
        @JvmOverloads
        fun toCodeDetail(status: Status, err: Err? = null): CodeDetail {
            return when (err) {
                is Err.ErrorList ->
                    CodeDetail(
                        path = status.path,
                        code = status.code,
                        success = status.success,
                        message = status.message,
                        detail = err.message,
                        errors = err.errors.map { ErrorDetail((it as? Err.ErrorField)?.field, it.message) },
                    )
                else ->
                    CodeDetail(
                        path = status.path,
                        code = status.code,
                        success = status.success,
                        message = status.message,
                        detail = err?.message,
                        instance = err?.ref?.toString(),
                    )
            }
        }
    }
}

