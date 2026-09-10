package kiit.codes.formats

import kiit.codes.Err
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport

/**
 * Minimal contract for one error-list entry, used by both [Problem] and [CodeDetail]. Only
 * `field` (what this is about) and `message` (what's wrong), matching the one thing gRPC's
 * `BadRequest.FieldViolation` and JSON:API's error objects agree on for a per-item shape. No
 * identifier, no doc-link, no timestamp: those belong on the parent [Problem]/[CodeDetail], not
 * repeated per item.
 *
 * `Err.cause` and `Err.ErrorField.value` never appear here on purpose: a raw exception or the
 * value that failed validation are both real disclosure risks to echo back by default. `Err.ref`
 * is excluded too, but that's exactly what a custom `mapper: (Err) -> T` reads to build a richer
 * type when a consumer decides more detail is safe for their own audience. [ErrorDetail] is
 * kiit-codes' own default `T`; supply your own type instead when you need more than this.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
interface ErrorItem {
    val field: String?
    val message: String
}

/** Default [ErrorItem]: just `field` + `message`, see [ErrorItem] for why. */
@OptIn(ExperimentalJsExport::class)
@JsExport
data class ErrorDetail(
    override val field: String?,
    override val message: String,
) : ErrorItem

/** Builds the default [ErrorDetail] from one [Err], used by the non-generic converter overloads. */
internal fun defaultErrorItem(err: Err): ErrorDetail =
    ErrorDetail((err as? Err.ErrorField)?.field, err.message)
