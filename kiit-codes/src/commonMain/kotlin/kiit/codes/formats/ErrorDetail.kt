package kiit.codes.formats

import kiit.codes.Err

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
interface ErrorItem {
    val field: String?
    val message: String
}

/** Default [ErrorItem]: just `field` + `message`, see [ErrorItem] for why. */
data class ErrorDetail(
    override val field: String?,
    override val message: String,
) : ErrorItem

/**
 * Builds the default [ErrorDetail] from one [Err], used by the non-generic converter overloads. The field comes from
 * an [Err.ErrorField], and any other [Err] has a `null` field. The converters flatten lists first (see [leaves]), so
 * this never receives an [Err.ErrorList].
 */
internal fun defaultErrorItem(err: Err): ErrorDetail = ErrorDetail((err as? Err.ErrorField)?.field, err.message)

/**
 * Flattens an [Err] into its leaf errors: an [Err.ErrorList] is expanded, recursively, and any other [Err] is one
 * entry. So an [Err.ErrorField] from `Err.on("email", "Missing")` is one entry that keeps its field, and a list
 * nested inside a list does not collapse into a single entry.
 */
internal fun Err.leaves(): List<Err> =
    when (this) {
        is Err.ErrorList -> errors.flatMap { it.leaves() }
        else -> listOf(this)
    }

/** The `detail`, `instance` and `errors` of a [Problem] or [CodeDetail], built from one optional [Err]. */
internal data class ErrParts<T : ErrorItem>(
    val detail: String?,
    val instance: String?,
    val errors: List<T>?,
)

/**
 * Splits an optional [Err] into the parts the converters share.
 *
 * 1. `errors`: every leaf of the error through [mapper], or `null` when there is no leaf.
 * 2. `detail`: the error's own message when it isn't blank, otherwise the first leaf message that isn't blank.
 * 3. `instance`: `ref` of a single error as text. An [Err.ErrorList] has none.
 * 4. No error gives all three as `null`.
 */
internal fun <T : ErrorItem> Err?.toParts(mapper: (Err) -> T): ErrParts<T> {
    if (this == null) return ErrParts(null, null, null)
    val leaves = leaves()
    val detail = (sequenceOf(message) + leaves.asSequence().map { it.message }).firstOrNull { it.isNotBlank() }
    return ErrParts(
        detail = detail,
        instance = if (this is Err.ErrorList) null else ref?.toString(),
        errors = if (leaves.isEmpty()) null else leaves.map(mapper),
    )
}
