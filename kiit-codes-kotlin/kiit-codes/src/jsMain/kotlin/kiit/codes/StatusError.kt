@file:OptIn(ExperimentalJsExport::class)

package kiit.codes

import kotlin.js.ExperimentalJsExport

/**
 * JS/TypeScript-idiomatic aliases for the [StatusException] subclasses.
 *
 * JavaScript error types are conventionally named `XxxError`. Each subclass is annotated with
 * `@JsExport`, and redeclares `status`/`errors` under those exact names via `@JsName`, since
 * [StatusException] itself isn't exported and its inherited members wouldn't otherwise appear
 * in the generated `.d.ts`. Everything nests under the `kiit.codes` namespace, so a single
 * `kiit` import covers the whole library:
 * ```ts
 * import { kiit } from '@kiit/codes'
 *
 * throw new kiit.codes.RestrictedError(kiit.codes.Failed.Restricted.UNAUTHENTICATED)
 *
 * try { ... } catch (e) {
 *     if (e instanceof kiit.codes.RestrictedError) { console.log(e.status.name) }
 * }
 * ```
 */
@JsExport
@JsName("RestrictedError")
class RestrictedError(
    status: Failed.Restricted,
    errors: List<Err> = emptyList(),
    cause: Throwable? = null,
) : StatusException.RestrictedException(status, errors, cause) {
    @JsName("status")
    val restrictedStatus: Failed.Restricted = status

    @JsName("errors")
    val exportedErrors: List<Err> get() = checked.errors
}

@JsExport
@JsName("InvalidError")
class InvalidError(
    status: Failed.Invalid,
    errors: List<Err> = emptyList(),
    cause: Throwable? = null,
) : StatusException.InvalidException(status, errors, cause) {
    @JsName("status")
    val invalidStatus: Failed.Invalid = status

    @JsName("errors")
    val exportedErrors: List<Err> get() = checked.errors
}

@JsExport
@JsName("RejectedError")
class RejectedError(
    status: Failed.Rejected,
    errors: List<Err> = emptyList(),
    cause: Throwable? = null,
) : StatusException.RejectedException(status, errors, cause) {
    @JsName("status")
    val rejectedStatus: Failed.Rejected = status

    @JsName("errors")
    val exportedErrors: List<Err> get() = checked.errors
}

@JsExport
@JsName("UnservedError")
class UnservedError(
    status: Failed.Unserved,
    errors: List<Err> = emptyList(),
    cause: Throwable? = null,
) : StatusException.UnservedException(status, errors, cause) {
    @JsName("status")
    val unservedStatus: Failed.Unserved = status

    @JsName("errors")
    val exportedErrors: List<Err> get() = checked.errors
}
