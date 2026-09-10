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
 * kiit-native counterpart to [Problem]: `path`/`code`/`message` instead of RFC 9457's
 * `type`/`title`/`status`.
 *
 * ```json
 * {
 *     "path": "com.stripe:payments.cards",
 *     "code": "Failed:Rejected:DUPLICATE_CHARGE",
 *     "success": false,
 *     "message": "This charge has already been processed"
 * }
 * ```
 *
 * 1. No HTTP status code, meant for internal service-to-service calls.
 * 2. Also useful for background jobs and other non-API calls where an HTTP status isn't relevant.
 * 3. Self-contained, no need for a public URI: [Status.origin]:[Status.scope] is enough.
 */
@JsExport
data class CodeDetail<T : ErrorItem>(
    val path: String,
    val code: String,
    val success: Boolean,
    val message: String,
    val detail: String? = null,
    val instance: String? = null,
    val errors: List<T>? = null,
)

/** Builds the default [CodeDetail]\<[ErrorDetail]\> for [status] (and optionally [err]). */
@JsExport
@JvmOverloads
fun toCodeDetail(
    status: Status,
    err: Err? = null,
): CodeDetail<ErrorDetail> {
    return toCodeDetail(status, err, ::defaultErrorItem)
}

/**
 * Builds a [CodeDetail]\<[T]\> for [status], mapping each entry of an [Err.ErrorList] through
 * [mapper]. Use this when the default [ErrorDetail] shape (field + message only) isn't enough —
 * `err.ref` is the usual place to stash whatever extra context [mapper] needs.
 */
fun <T : ErrorItem> toCodeDetail(status: Status, err: Err?, mapper: (Err) -> T): CodeDetail<T> {
    return when (err) {
        is Err.ErrorList ->
            CodeDetail(
                path = status.path,
                code = status.code,
                success = status.success,
                message = status.message,
                detail = err.message,
                errors = err.errors.map(mapper),
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
