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
 * Each subclass is `open`, so a domain-specific subclass can add its own data. [status] is typed to the
 * group (`RestrictedException.status` is a [Failed.Restricted]), and `message` is the one-line summary, which
 * defaults to the status title. [errors] is the full list and is independent of `message`:
 *
 * ```kotlin
 * class AccountLockedException(val accountId: String) :
 *     StatusException.RestrictedException(Restricted.DENIED, message = "Account $accountId is locked")
 * ```
 *
 * [checked] carries the same status and errors as one [Checked], for code that works on that type.
 *
 * Prefer the platform-specific equivalents over these base classes directly: `RestrictedError`/
 * `InvalidError`/`RejectedError`/`UnservedError` in `iosMain` for idiomatic Swift naming via
 * `@ObjCName`, or the equivalents in the native TypeScript port (`ports/kiit-codes-ts`) for JS/TS.
 */
sealed class StatusException(
    failed: Failed,
    message: String?,
    errors: List<Err>,
    cause: Throwable?,
) : Exception(message ?: failed.title, cause) {
    /** The failed status, narrowed to this exception's group (e.g. [Failed.Restricted]). */
    abstract val status: Failed

    /**
     * The same [status] and [errors] as one [Checked]. `checked.status` is the same object as [status],
     * typed [Status] since a [Checked] can also represent a pass.
     */
    val checked: Checked = Checked.failure(failed, errors.ifEmpty { listOf(Err.of(failed)) })

    /** Every error for this failure. Defaults to the status title when none were given. */
    val errors: List<Err> get() = checked.errors

    /** Thrown for a [Failed.Restricted] status: a security or access-control failure. */
    open class RestrictedException
        @JvmOverloads
        constructor(
            final override val status: Failed.Restricted,
            message: String? = null,
            errors: List<Err> = emptyList(),
            cause: Throwable? = null,
        ) : StatusException(status, message, errors, cause)

    /** Thrown for a [Failed.Invalid] status: the request as given cannot be satisfied. */
    open class InvalidException
        @JvmOverloads
        constructor(
            final override val status: Failed.Invalid,
            message: String? = null,
            errors: List<Err> = emptyList(),
            cause: Throwable? = null,
        ) : StatusException(status, message, errors, cause)

    /** Thrown for a [Failed.Rejected] status: a known, expected business-rule failure. */
    open class RejectedException
        @JvmOverloads
        constructor(
            final override val status: Failed.Rejected,
            message: String? = null,
            errors: List<Err> = emptyList(),
            cause: Throwable? = null,
        ) : StatusException(status, message, errors, cause)

    /** Thrown for a [Failed.Unserved] status: valid and permitted, but can't be serviced right now. */
    open class UnservedException
        @JvmOverloads
        constructor(
            final override val status: Failed.Unserved,
            message: String? = null,
            errors: List<Err> = emptyList(),
            cause: Throwable? = null,
        ) : StatusException(status, message, errors, cause)
}

/**
 * Converts a bare [Failed] status into the matching [StatusException] subclass, so callers don't
 * need to write the `when` themselves.
 *
 * Deliberately has no `else` branch: if [Failed] ever gains a new subtype, this becomes a
 * compile error to fix here, not something a wildcard branch would silently mishandle.
 */
@JvmOverloads
fun Failed.toException(message: String? = null, errors: List<Err> = emptyList()): StatusException =
    when (this) {
        is Failed.Restricted -> StatusException.RestrictedException(this, message, errors)
        is Failed.Invalid -> StatusException.InvalidException(this, message, errors)
        is Failed.Rejected -> StatusException.RejectedException(this, message, errors)
        is Failed.Unserved -> StatusException.UnservedException(this, message, errors)
    }
