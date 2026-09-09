/** url: www.kiit.dev */
@file:OptIn(ExperimentalJsExport::class)
@file:JvmName("CodeDetails")

package kiit.codes

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads

/**
 * kiit-native counterpart to [ProblemDetail]: the same occurrence-detail shape, in kiit-codes'
 * own vocabulary ([Status.path]/[Status.code]/[Status.message]) instead of RFC 9457's
 * (`type`/`title`/`status`). No HTTP status code — this is meant for internal service-to-service
 * calls, background jobs, and any other non-API path where an HTTP status wouldn't mean anything.
 *
 * Shape:
 * ```json
 * {
 *      "path"    : "com.stripe:payments.cards",
 *      "code"    : "Failed:Rejected:DUPLICATE_CHARGE",
 *      "success" : false,
 *      "message" : "This charge has already been processed"
 * }
 * ```
 *
 * Opt-in output shape, never returned by default from anything — build one explicitly via
 * [toCodeDetail].
 */
@JsExport
data class CodeDetail(
    val path: String,
    val code: String,
    val success: Boolean,
    val message: String,
    val detail: String? = null,
    val instance: String? = null,
    val errors: List<ProblemError>? = null,
)

/**
 * Builds a [CodeDetail] for [status] (and optionally [err]) — kiit-codes' own equivalent of
 * [toProblemDetail], for callers with no use for an HTTP status code or a URI `type`. [status]
 * and [Status] itself are never modified.
 *
 * Field mapping:
 * - [Status.path] -> [CodeDetail.path].
 * - [Status.code] -> [CodeDetail.code].
 * - [Status.success] -> [CodeDetail.success], a quick, convenient `Passed`/`Failed` check without
 *   pattern matching on [status] itself.
 * - [Status.message] -> [CodeDetail.message].
 * - [Err.message] -> [CodeDetail.detail] (occurrence-specific, never on [status] itself).
 * - [Err.ref] -> [CodeDetail.instance].
 * - [Err.ErrorList.errors] -> [CodeDetail.errors], any other [Err] shape leaves it null.
 *
 * [ProblemError] is reused here rather than duplicated — it's already kiit-codes' own name for
 * one wrapped [Err] entry (field + detail), not an RFC-specific shape.
 */
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
                errors = err.errors.map { ProblemError((it as? Err.ErrorField)?.field, it.message) },
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
