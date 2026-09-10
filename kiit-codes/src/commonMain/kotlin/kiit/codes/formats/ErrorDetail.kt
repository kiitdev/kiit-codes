package kiit.codes.formats

import kiit.codes.Err
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport


/**
 * kiit-side name for one entry in RFC 9457's `errors` extension. [field] is null when the source
 * [Err] isn't field-scoped, see [toProblemDetail].
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
data class ErrorDetail(
    val field: String?,
    val detail: String,
)
