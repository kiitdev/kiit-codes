/** url: www.kiit.dev */
@file:JvmName("Checks")
@file:OptIn(ExperimentalJsExport::class, ExperimentalJsStatic::class)

package kiit.codes

import kotlin.js.ExperimentalJsExport
import kotlin.js.ExperimentalJsStatic
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * Non-monadic result of a validation-style check that reports every problem found rather than
 * short-circuiting on the first one.
 *
 * 1. A form with three invalid fields should report all three, not just the first. Deliberately
 *    has no `map`/`flatMap`. Compose multiple checks with [collect] instead of chaining.
 * 2. Construction is only through [success]/[failure] so [status] and [errors] can never be out
 *    of sync: a passing [Checked] always has an empty [errors] list, a failing one always has
 *    at least one entry in it.
 * 3. Implements [HasErrors] and [HasStatus]. [status] is typed [Status] rather than narrowed to
 *    [Passed]/[Failed], since one [Checked] instance can represent either outcome.
 */
@JsExport
class Checked private constructor(override val status: Status, override val errors: List<Err>) : HasErrors, HasStatus<Status> {
    val isValid: Boolean get() = errors.isEmpty()

    companion object {
        /** A passing check with no errors. */
        @JvmStatic
        @JsStatic
        @JvmOverloads
        fun success(status: Passed = Succeeded.SUCCESS): Checked = Checked(status, emptyList())

        /** A failing check with one or more [errors]. */
        @JvmStatic
        @JsStatic
        fun failure(status: Failed, errors: List<Err>): Checked {
            require(errors.isNotEmpty()) { "failure requires at least one Err" }
            return Checked(status, errors)
        }
    }
}

/** [collect] over varargs, the JS/TS-friendlier shape (see the [List] overload below). */
@JsExport
fun collect(vararg checks: Checked): Checked = collect(checks.toList())

/**
 * Collects multiple [checks] into one: passes only if every one of them passed, otherwise fails
 * with [Failed.Invalid.INVALID_VALUE] and every error from every failing entry pooled together,
 * in the order the checks were given. `@JsName` avoids a JS name clash with the vararg overload.
 */
@JsExport
@JsName("collectList")
fun collect(checks: List<Checked>): Checked {
    val errors = checks.flatMap { it.errors }
    return if (errors.isEmpty()) Checked.success() else Checked.failure(Invalid.INVALID_VALUE, errors)
}
