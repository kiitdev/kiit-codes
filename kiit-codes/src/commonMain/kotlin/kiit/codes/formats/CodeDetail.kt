/** url: www.kiit.dev */
@file:JvmName("CodeDetails")

package kiit.codes.formats

import kiit.codes.CodesToHttp
import kiit.codes.Err
import kiit.codes.Status
import kiit.codes.code
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads

/**
 * kiit-native counterpart to [Problem]: `code` instead of RFC 9457's `type`, and `status` is optional.
 *
 * ```json
 * {
 *     "code": "stripe.com:payments.cards:Failed:Rejected:DUPLICATE_CHARGE",
 *     "success": false,
 *     "title": "This charge has already been processed"
 * }
 * ```
 *
 * 1. Meant for internal service-to-service calls.
 * 2. Also useful for background jobs and other non-API calls where an HTTP status isn't relevant.
 * 3. Self-contained, no need for a public URI: [code] is [Status.origin], [Status.scope] and
 *    [Status.code], so it names the origin as well as the status. An empty scope leaves an empty
 *    value (`myapp1::Failed:Rejected:OUT_OF_STOCK`), so `code` always has five `:`-separated values.
 * 4. [status] is optional — pass a `mapping` to [toCodeDetail] when this shape is still going out
 *    over HTTP and the status code is worth carrying alongside it.
 */
data class CodeDetail<T : ErrorItem>(
    val code: String,
    val success: Boolean,
    val title: String,
    val detail: String? = null,
    val instance: String? = null,
    val errors: List<T>? = null,
    val status: Int? = null,
)

/** The exact identity of a status, `{origin}:{scope}:{Status.code}`. Shared by [CodeDetail.code] and [Problem.code]. */
internal fun Status.detailCode(): String = "$origin:$scope:$code"

/**
 * Builds the default [CodeDetail]\<[ErrorDetail]\> for [status] (and optionally [err]). Pass
 * [mapping] to also populate [CodeDetail.status] with the equivalent HTTP status code.
 */
@JvmOverloads
fun toCodeDetail(status: Status, err: Err? = null, mapping: CodesToHttp? = null): CodeDetail<ErrorDetail> {
    return toCodeDetail(status, err, mapping, ::defaultErrorItem)
}

/**
 * Builds a [CodeDetail]\<[T]\> for [status], mapping each entry of an [Err.ErrorList] through
 * [mapper]. Use this when the default [ErrorDetail] shape (field + message only) isn't enough —
 * `err.ref` is the usual place to stash whatever extra context [mapper] needs. Pass [mapping] to
 * also populate [CodeDetail.status].
 */
@Suppress("ktlint:standard:function-signature")
fun <T : ErrorItem> toCodeDetail(
    status: Status,
    err: Err?,
    mapping: CodesToHttp? = null,
    mapper: (Err) -> T,
): CodeDetail<T> {
    val httpStatus = mapping?.toCode(status)
    return when (err) {
        is Err.ErrorList ->
            CodeDetail(
                code = status.detailCode(),
                success = status.success,
                title = status.title,
                detail = err.message,
                errors = err.errors.map(mapper),
                status = httpStatus,
            )
        else ->
            CodeDetail(
                code = status.detailCode(),
                success = status.success,
                title = status.title,
                detail = err?.message,
                instance = err?.ref?.toString(),
                status = httpStatus,
            )
    }
}
