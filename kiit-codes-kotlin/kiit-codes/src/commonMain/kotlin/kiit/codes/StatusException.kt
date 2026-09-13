@file:JvmName("StatusExceptions")

package kiit.codes

import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads

/**
 * Sealed exception hierarchy carrying a [Checked] instead of a plain message string.
 *
 * Throw one of the typed subclasses ([RestrictedException], [InvalidException],
 * [RejectedException], [UnservedException]) when a [Failed] status needs to cross a call
 * boundary that can only communicate via exceptions (e.g. from a service layer into a
 * framework that catches [Exception]). [StatusException] itself is sealed and cannot be thrown
 * or caught as a standalone concrete type. Catch it to narrow exhaustively over all four
 * subclasses, or catch one subclass directly to only handle that group:
 *
 * ```kotlin
 * throw StatusException.RestrictedException(Restricted.UNAUTHORIZED)
 *
 * try {
 *     // ...
 * } catch (e: StatusException) {
 *     when (e) {
 *         is StatusException.RestrictedException -> // handle auth failure
 *         is StatusException.InvalidException    -> // handle bad input
 *         is StatusException.RejectedException   -> // handle known business-rule failure
 *         is StatusException.UnservedException   -> // handle capacity / timeout / unsupported / unexpected
 *     }
 * }
 *
 * // narrow catch, no `when` needed
 * catch (e: StatusException.RestrictedException) { /* ... */ }
 * ```
 *
 * Use [Failed.toException] to convert a bare [Failed] status into the matching subclass without
 * writing the `when` yourself.
 *
 * Each subclass is `open`, so a one-line domain-specific subclass remains possible, e.g.
 * `class RegistrationException(status: Failed.Restricted, ...) : StatusException.RestrictedException(...)`.
 *
 * Prefer the platform-specific equivalents over these base classes directly: `RestrictedError`/
 * `InvalidError`/`RejectedError`/`UnservedError` in `iosMain` for idiomatic Swift naming via
 * `@ObjCName`, or the equivalents in `jsMain` for JS/TS.
 */
sealed class StatusException(
    val checked: Checked,
    cause: Throwable? = null,
) : Exception(checked.status.message, cause) {
    val status: Status get() = checked.status
    val errors: List<Err> get() = checked.errors

    /** Thrown for a [Failed.Restricted] status: a security or access-control failure. */
    open class RestrictedException
        @JvmOverloads
        constructor(
            status: Failed.Restricted,
            errors: List<Err> = emptyList(),
            cause: Throwable? = null,
        ) : StatusException(Checked.failure(status, errors.ifEmpty { listOf(Err.of(status)) }), cause)

    /** Thrown for a [Failed.Invalid] status: the request as given cannot be satisfied. */
    open class InvalidException
        @JvmOverloads
        constructor(
            status: Failed.Invalid,
            errors: List<Err> = emptyList(),
            cause: Throwable? = null,
        ) : StatusException(Checked.failure(status, errors.ifEmpty { listOf(Err.of(status)) }), cause)

    /** Thrown for a [Failed.Rejected] status: a known, expected business-rule failure. */
    open class RejectedException
        @JvmOverloads
        constructor(
            status: Failed.Rejected,
            errors: List<Err> = emptyList(),
            cause: Throwable? = null,
        ) : StatusException(Checked.failure(status, errors.ifEmpty { listOf(Err.of(status)) }), cause)

    /** Thrown for a [Failed.Unserved] status: valid and permitted, but can't be serviced right now. */
    open class UnservedException
        @JvmOverloads
        constructor(
            status: Failed.Unserved,
            errors: List<Err> = emptyList(),
            cause: Throwable? = null,
        ) : StatusException(Checked.failure(status, errors.ifEmpty { listOf(Err.of(status)) }), cause)
}

/**
 * Converts a bare [Failed] status into the matching [StatusException] subclass, so callers don't
 * need to write the `when` themselves.
 *
 * Deliberately has no `else` branch: if [Failed] ever gains a new subtype, this becomes a
 * compile error to fix here, not something a wildcard branch would silently mishandle.
 */
@JvmOverloads
fun Failed.toException(errors: List<Err> = emptyList()): StatusException =
    when (this) {
        is Failed.Restricted -> StatusException.RestrictedException(this, errors)
        is Failed.Invalid -> StatusException.InvalidException(this, errors)
        is Failed.Rejected -> StatusException.RejectedException(this, errors)
        is Failed.Unserved -> StatusException.UnservedException(this, errors)
    }
