package kiit.codes

/**
 * Swift-idiomatic aliases for the [StatusException] subclasses.
 *
 * 1. Swift error types are conventionally named `XxxError` (e.g. `URLError`, `CocoaError`).
 *    `@ObjCName` prevents the auto-generated `KiitCodesXxxError` prefix in the ObjC header,
 *    giving Swift consumers the clean names below.
 *
 * Swift usage, construction and property access work directly:
 * ```swift
 * let e = RestrictedError(status: Failed.Restricted.companion.UNAUTHENTICATED, message: nil, errors: [], cause: nil)
 * print(e.status.name)  // "UNAUTHENTICATED"
 * ```
 *
 * 2. Manually `throw`-ing a constructed instance does **not** work. `throw restrictedError as!
 *    Error` compiles since the cast can't be verified statically, but it crashes at runtime.
 *    Kotlin exception types just don't bridge to Swift's `Error` protocol this way, SKIE or not.
 * 3. SKIE's real throws-bridging feature lets a Kotlin function annotated `@Throws(...)` become
 *    a native Swift `throws` function with no manual casting, but kiit-codes doesn't have one
 *    of those yet. See `samples/sample-swift` for what does work today.
 */
@OptIn(kotlin.experimental.ExperimentalObjCName::class)
@ObjCName("RestrictedError", swiftName = "RestrictedError")
class RestrictedError(
    status: Failed.Restricted,
    message: String? = null,
    errors: List<Err> = emptyList(),
    cause: Throwable? = null,
) : StatusException.RestrictedException(status, message, errors, cause)

@OptIn(kotlin.experimental.ExperimentalObjCName::class)
@ObjCName("InvalidError", swiftName = "InvalidError")
class InvalidError(
    status: Failed.Invalid,
    message: String? = null,
    errors: List<Err> = emptyList(),
    cause: Throwable? = null,
) : StatusException.InvalidException(status, message, errors, cause)

@OptIn(kotlin.experimental.ExperimentalObjCName::class)
@ObjCName("RejectedError", swiftName = "RejectedError")
class RejectedError(
    status: Failed.Rejected,
    message: String? = null,
    errors: List<Err> = emptyList(),
    cause: Throwable? = null,
) : StatusException.RejectedException(status, message, errors, cause)

@OptIn(kotlin.experimental.ExperimentalObjCName::class)
@ObjCName("UnservedError", swiftName = "UnservedError")
class UnservedError(
    status: Failed.Unserved,
    message: String? = null,
    errors: List<Err> = emptyList(),
    cause: Throwable? = null,
) : StatusException.UnservedException(status, message, errors, cause)
