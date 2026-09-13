/** url: www.kiit.dev */
@file:OptIn(ExperimentalJsExport::class, ExperimentalJsStatic::class)

package kiit.codes

import kotlin.js.ExperimentalJsExport
import kotlin.js.ExperimentalJsStatic
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsStatic
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * Capability interface for any type that carries a list of [Err]. Implemented by [Checked] so a
 * validation-style result can be worked with generically alongside other error-carrying types.
 */
@JsExport
interface HasErrors {
    val errors: List<Err>
}

/**
 * Capability interface for a domain type (success or error) that carries its own [Status], so a
 * sealed hierarchy of domain-specific outcomes can pair each variant with its matching kiit-codes
 * category at definition time, instead of choosing one per call site. [S] narrows this to
 * [Passed] for domain success types or [Failed] for domain error types. Used by kiit-result's
 * `success`/`failure`/`build` helpers to wire a domain type's own status into a `Result`.
 */
@JsExport
interface HasStatus<out S : Status> {
    val status: S
}

/**
 * Err is an error representation for use with `Result`/`Outcome`-style types and can be
 * created from
 * 1. simple strings
 * 2. exceptions
 * 3. field with name/value
 * 4. list of strings or Errs
 */
@JsExport
sealed class Err {
    abstract val message: String
    abstract val cause: Throwable?
    abstract val ref: Any?

    /** Default [Err] implementation: a message with an optional cause. */
    data class ErrorInfo
        @JvmOverloads
        constructor(
            override val message: String,
            override val cause: Throwable? = null,
            override val ref: Any? = null,
        ) : Err()

    /**
     * [Err] implementation for an error on a specific field.
     * @param field: Name of the field causing the error, e.g. "email"
     * @param value: Value of the field causing the error, e.g. "some_invalid_value"
     */
    data class ErrorField
        @JvmOverloads
        constructor(
            val field: String,
            val value: String,
            override val message: String,
            override val cause: Throwable? = null,
            override val ref: Any? = null,
        ) : Err()

    /**
     * [Err] implementation that wraps a list of other errors.
     * @param errors: The errors being wrapped
     */
    data class ErrorList
        @JvmOverloads
        constructor(
            val errors: List<Err>,
            override val message: String,
            override val cause: Throwable? = null,
            override val ref: Any? = null,
        ) : Err()

    /**
     * Convenience builders for [Err] from common sources: strings, exceptions, field errors.
     */
    companion object {
        @JvmStatic
        @JsStatic
        @JvmOverloads
        fun of(message: String, ex: Throwable? = null): Err {
            return ErrorInfo(message, ex)
        }

        // @JsName avoids a JS name clash with the of(message, ex) overload above.
        @JvmStatic
        @JsStatic
        @JsName("ofStatus")
        fun of(status: Status): Err {
            return ErrorInfo(status.message)
        }

        @JvmStatic
        @JsStatic
        @JvmOverloads
        fun on(field: String, value: String, message: String, ex: Throwable? = null): Err {
            return ErrorField(field, value, message, ex)
        }

        /**
         * Builds an [Err] for a [field] with a specific per-occurrence [message], without a
         * [value]. Use this instead of [on] when the value itself could be sensitive (e.g. a
         * password or token) and shouldn't be carried on the error. `@JsName` avoids a JS name
         * clash above.
         */
        @JvmStatic
        @JsStatic
        @JvmOverloads
        @JsName("onField")
        fun on(field: String, message: String, ex: Throwable? = null): Err {
            return ErrorField(field, "", message, ex)
        }

        @JvmStatic
        @JsStatic
        fun ex(ex: Throwable): Err {
            return ErrorInfo(ex.message ?: "", ex)
        }

        @JvmStatic
        @JsStatic
        fun obj(err: Any): Err {
            return ErrorInfo(err.toString(), null, err)
        }

        @JvmStatic
        @JsStatic
        fun list(errors: List<String>, message: String?): ErrorList {
            return ErrorList(errors.map { ErrorInfo(it) }, message ?: "Error occurred")
        }

        @JvmStatic
        @JsStatic
        fun build(error: Any?): Err {
            return when (error) {
                null -> of(Unserved.UNEXPECTED.message)
                is Err -> error
                is String -> of(error)
                is Exception -> ex(error)
                else -> obj(error)
            }
        }
    }
}
