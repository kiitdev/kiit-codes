/** url: www.kiit.dev */
@file:OptIn(ExperimentalJsExport::class, ExperimentalJsStatic::class)

package kiit.codes

import kotlin.js.ExperimentalJsExport
import kotlin.js.ExperimentalJsStatic
import kotlin.js.JsExport
import kotlin.js.JsStatic
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic

/** Well-known [Status.origin] values. */
object StatusConstants {
    /**
     * Origin for every built-in [Codes] entry. Reverse-DNS, matching kiit-codes' own naming
     * guideline for [Status.origin] (mirrors Gradle's `groupId`, `dev.kiit`).
     */
    const val KIIT = "dev.kiit"

    /** Default origin for consumer/custom statuses that don't specify one explicitly. */
    const val CUSTOM = "custom"
}

/**
 * Platform-agnostic status type describing the outcome of any operation: a service call,
 * a background job step, an API request, or a CLI command.
 *
 * Shape (maps directly to JSON / API error responses):
 * {
 *      "name"    : "DENIED",
 *      "group"   : "Restricted",
 *      "origin"  : "dev.kiit",
 *      "scope"   : "",
 *      "message" : "The request was denied.",
 *      "success" : false
 * }
 *
 * Hierarchy. Groups are closed/sealed and fixed by design, to enforce a consistent taxonomy
 * across every consumer. Individual codes *within* a group are open. Create new domain codes
 * by constructing a [Passed] or [Failed] subtype directly (see [Codes] for the built-in set):
 *
 *   Status  = Passed     | Failed
 *   Passed  = Succeeded  | Pending | Excluded | Information
 *   Failed  = Restricted | Invalid | Rejected | Unserved
 */
@JsExport
sealed interface Status {
    /**
     * Unique domain label, e.g. "TOKEN_EXPIRED", "RATE_LIMITED".
     * SCREAMING_SNAKE_CASE and stable, used as a searchable/aggregable key in logs and metrics.
     */
    val name: String

    /**
     * Origin of this status, e.g. [StatusConstants.KIIT] for every built-in [Codes] entry.
     * Consumer/custom subtypes default to [StatusConstants.CUSTOM] rather than silently inheriting
     * [StatusConstants.KIIT], so a status can never accidentally misrepresent where it came from.
     */
    val origin: String

    /**
     * Optional, free-form internal-organization label — a department, product area, route, or
     * anything else the consumer wants to attach, e.g. `"payments"`, `"payments.cards"`.
     * `kiit-codes` never parses or enforces [scope]'s internal shape, only that it doesn't
     * contain `:` (reserved, see [path]).
     *
     * Empty string means unset — defaults to `""` on every built-in and on any [Passed]/[Failed]
     * subtype that doesn't set it explicitly. A real, defaulted field rather than a separate
     * capability interface, since consumers construct concrete [Passed]/[Failed] subtypes
     * directly (or `.copy()` an existing instance) and can't retroactively add an interface to
     * one afterwards.
     */
    val scope: String

    /**
     * Human-readable constant description, never constructed from runtime data. Per-instance
     * detail belongs on whatever wraps this Status, not here. Do not use this as a key, use
     * [name] instead.
     */
    val message: String

    /**
     * True for all [Passed] subtypes, false for all [Failed] subtypes. Callers that don't need
     * to narrow the sealed type can branch on this directly instead of pattern matching.
     */
    val success: Boolean

    /** The group discriminant, e.g. "Restricted", "Rejected". See the hierarchy above. */
    val group: String

    companion object {
        /**
         * Resolves a status from an optional [message] override and an optional [rawStatus]
         * override, falling back to [status] when neither is supplied. [rawStatus], if present,
         * is used as the base instead of [status]; [message], if present, is then applied on top
         * of that base.
         */
        @JvmStatic
        @Suppress("UNCHECKED_CAST")
        fun <T : Status> ofStatus(message: String?, rawStatus: T?, status: T): T {
            val base = rawStatus ?: status
            return if (message == null) base else withMessage(base, message) as T
        }

        /**
         * Internal-only, narrower stand-in for the copy capability [Status] used to expose
         * publicly. That was removed since an immutable [Status] shouldn't offer a general way
         * to mutate itself. Only [ofStatus] needs this, and only ever to override [message],
         * never [origin].
         */
        private fun withMessage(status: Status, message: String): Status =
            when (status) {
                is Passed.Succeeded -> status.copy(message = message)
                is Passed.Pending -> status.copy(message = message)
                is Passed.Excluded -> status.copy(message = message)
                is Passed.Information -> status.copy(message = message)
                is Failed.Restricted -> status.copy(message = message)
                is Failed.Invalid -> status.copy(message = message)
                is Failed.Rejected -> status.copy(message = message)
                is Failed.Unserved -> status.copy(message = message)
            }
    }
}

/**
 * Module-internal identity, replacing the old `"$origin.$group.$name"` string key. A real data
 * class gets correct `equals`/`hashCode` for free and includes [scope] so a [Status] with a
 * non-empty [Status.scope] can't silently collide with one that shares only
 * [origin]/[group]/[name]. Never public, never serialized — used by [Codes]' own registry lookup.
 * [CodesToHttp]/[CodesToGrpc]'s `overrides` maps stay `Map<String, Int>` (an existing, published
 * constructor signature this release can't break), so they use [Status.key] instead — a string
 * built from the same fields as this class.
 */
internal data class StatusKey(
    val origin: String,
    val scope: String,
    val group: String,
    val name: String,
)

/**
 * Builds this [Status]'s [StatusKey]. An extension rather than an interface member since Kotlin
 * interfaces can't declare `internal` members.
 */
internal val Status.statusKey: StatusKey
    get() = StatusKey(origin = origin, scope = scope, group = group, name = name)

/**
 * String form of [statusKey], for [CodesToHttp]/[CodesToGrpc]'s `overrides` maps, whose
 * constructor/`DEFAULT_OVERRIDES` are public and therefore can't declare an `internal`-typed
 * key (Kotlin forbids exposing an internal type in a public signature) without breaking that
 * already-published API. Same collision-safety as [StatusKey] otherwise: scoped by
 * origin+scope+group+name, not just origin+group+name like the old `id` this replaces.
 */
internal val Status.key: String
    get() = statusKey.let { "${it.origin}:${it.scope}:${it.group}:${it.name}" }

/**
 * Public, display-oriented, `:`-delimited identity for logging/debugging. Not for lookups or
 * comparison, since [Status.scope] is consumer-defined and can change over time — see [code] for
 * a value that's actually safe to compare across releases.
 *
 * Feeds into [toProblemDetail]'s RFC 9457 `type` construction by default (see
 * [defaultTypeBuilder]). If you rely on `type`'s stability, [Status.origin]/[Status.scope]
 * inherit that same obligation — they are consumer-defined and only as stable as you keep them.
 */
val Status.path: String
    get() = if (scope.isNotEmpty()) "$origin:$scope" else origin

/**
 * Public, `:`-delimited identity built entirely from hard-rule, compiler-enforced fields
 * ([success], [group], [name]). Safe to log, diff, or compare across releases, unlike [path].
 *
 * Not unique. Two [Status]es can share the same [code] while differing in [Status.origin]/
 * [Status.scope] (e.g. two different consumers both defining a `Failed.Rejected("CONFLICT", ...)`).
 * Do not use [code] for identity, lookup, or equality checks — compare fields directly, or use
 * the internal `StatusKey` (origin+scope+group+name) if you're inside this module.
 */
val Status.code: String
    get() = "${if (success) "Passed" else "Failed"}:$group:$name"

/** JS/TS-reachable proxy for [Status.path], see [Codes]'s KDoc for why these proxies exist. */
@JsExport
fun statusPath(status: Status): String = status.path

/** JS/TS-reachable proxy for [Status.code], see [Codes]'s KDoc for why these proxies exist. */
@JsExport
fun statusCode(status: Status): String = status.code

/**
 * Parent sealed type for all non-failure statuses (success = true for every subtype).
 * Subtypes: [Succeeded], [Pending], [Excluded], [Information].
 *
 * 1. Each subtype's built-in constants live on its own companion object, not on [Codes]. This
 *    keeps IDE autocomplete scoped, typing `Succeeded.` shows only [Succeeded]'s own members.
 *    [Codes] is an aggregate/lookup layer over these, not where they're declared.
 * 2. The package-level typealiases below (e.g. `Succeeded` for `Passed.Succeeded`) are the same
 *    type, not a copy. They exist purely to avoid writing the `Passed.`/`Failed.` prefix at
 *    every call site.
 */
@JsExport
sealed class Passed : Status {
    final override val success: Boolean get() = true

    final override val group: String
        get() =
            when (this) {
                is Succeeded -> "Succeeded"
                is Pending -> "Pending"
                is Excluded -> "Excluded"
                is Information -> "Information"
            }

    /** Runtime-accessible version of each group's meaning, see the subtypes' own KDoc for detail. */
    val groupDescription: String
        get() =
            when (this) {
                is Succeeded -> "The operation completed successfully."
                is Pending -> "The operation was accepted but has not yet fully resolved."
                is Excluded -> "The item was intentionally excluded from the operation."
                is Information -> "The response provides information; no operation was performed."
            }

    /** See [Passed.groupDescription] for this group's definition. */
    data class Succeeded(
        override val name: String,
        override val message: String,
        override val origin: String = StatusConstants.CUSTOM,
        override val scope: String = "",
    ) : Passed() {
        companion object {
            @JvmField
            @JsStatic
            val SUCCESS =
                Succeeded(
                    "SUCCESS",
                    "The operation completed successfully.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            @JsStatic
            val CREATED =
                Succeeded(
                    "CREATED",
                    "A new resource was created.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            @JsStatic
            val UPDATED =
                Succeeded(
                    "UPDATED",
                    "The resource was fully updated.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            @JsStatic
            val PATCHED =
                Succeeded(
                    "PATCHED",
                    "The resource was partially updated.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            @JsStatic
            val FETCHED =
                Succeeded(
                    "FETCHED",
                    "The resource was retrieved.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            @JsStatic
            val DELETED =
                Succeeded(
                    "DELETED",
                    "The resource was deleted.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            @JsStatic
            val HANDLED =
                Succeeded(
                    "HANDLED",
                    "The request was handled; nothing to return.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            @JsStatic
            val REFERRED =
                Succeeded(
                    "REFERRED",
                    "The result is at another location.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            @JsStatic
            val EXITED =
                Succeeded(
                    "EXITED",
                    "The application exited cleanly.",
                    origin = StatusConstants.KIIT,
                )
        }
    }

    /** See [Passed.groupDescription] for this group's definition. */
    data class Pending(
        override val name: String,
        override val message: String,
        override val origin: String = StatusConstants.CUSTOM,
        override val scope: String = "",
    ) : Passed() {
        companion object {
            @JvmField
            @JsStatic
            val ACCEPTED =
                Pending(
                    "ACCEPTED",
                    "The request was accepted.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            @JsStatic
            val QUEUED =
                Pending(
                    "QUEUED",
                    "The request is waiting to be processed.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            @JsStatic
            val PROCESSING =
                Pending(
                    "PROCESSING",
                    "The request is being processed.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            @JsStatic
            val CONFIRM =
                Pending(
                    "CONFIRM",
                    "The request is awaiting confirmation.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            @JsStatic
            val REDIRECTED =
                Pending(
                    "REDIRECTED",
                    "This request is being handled elsewhere.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            @JsStatic
            val SCHEDULED =
                Pending(
                    "SCHEDULED",
                    "The operation is scheduled for later.",
                    origin = StatusConstants.KIIT,
                )
        }
    }

    /**
     * See [Passed.groupDescription] for this group's definition.
     */
    data class Excluded(
        override val name: String,
        override val message: String,
        override val origin: String = StatusConstants.CUSTOM,
        override val scope: String = "",
    ) : Passed() {
        companion object {
            @JvmField
            @JsStatic
            val OMITTED =
                Excluded(
                    "OMITTED",
                    "The item was excluded from the result.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            @JsStatic
            val SKIPPED =
                Excluded(
                    "SKIPPED",
                    "The item was not processed.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            @JsStatic
            val DISCARDED =
                Excluded(
                    "DISCARDED",
                    "The item was processed, then excluded for unrelated reasons.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            @JsStatic
            val CANCELLED =
                Excluded(
                    "CANCELLED",
                    "The operation was cancelled by the caller before completion.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            @JsStatic
            val DEDUPLICATED =
                Excluded(
                    "DEDUPLICATED",
                    "The duplicate item was not processed.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            @JsStatic
            val DISQUALIFIED =
                Excluded(
                    "DISQUALIFIED",
                    "The item was disqualified.",
                    origin = StatusConstants.KIIT,
                )
        }
    }

    /** See [Passed.groupDescription] for this group's definition. */
    data class Information(
        override val name: String,
        override val message: String,
        override val origin: String = StatusConstants.CUSTOM,
        override val scope: String = "",
    ) : Passed() {
        companion object {
            @JvmField
            @JsStatic
            val NOTICE =
                Information(
                    "NOTICE",
                    "An informational notice.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            @JsStatic
            val ADVISORY =
                Information(
                    "ADVISORY",
                    "A notice that may need attention.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            @JsStatic
            val METADATA =
                Information(
                    "METADATA",
                    "Information about the application itself was returned.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            @JsStatic
            val HEALTH =
                Information(
                    "HEALTH",
                    "The service is healthy and operational.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            @JsStatic
            val DIAGNOSTICS =
                Information(
                    "DIAGNOSTICS",
                    "Diagnostic or operational information was returned.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            @JsStatic
            val MOVED =
                Information(
                    "MOVED",
                    "The resource has permanently moved to a new location.",
                    origin = StatusConstants.KIIT,
                )
        }
    }
}

/**
 * Parent sealed type for all failure statuses (success = false for every subtype).
 * Subtypes: [Restricted], [Invalid], [Rejected], [Unserved].
 *
 * See [Passed]'s doc for why built-in constants live on each subtype's own companion object
 * rather than on [Codes].
 */
@JsExport
sealed class Failed : Status {
    final override val success: Boolean get() = false

    final override val group: String
        get() =
            when (this) {
                is Restricted -> "Restricted"
                is Invalid -> "Invalid"
                is Rejected -> "Rejected"
                is Unserved -> "Unserved"
            }

    /** Runtime-accessible version of each group's meaning, see the subtypes' own KDoc for detail. */
    val groupDescription: String
        get() =
            when (this) {
                is Restricted -> "The caller is not allowed."
                is Invalid -> "The request itself is wrong."
                is Rejected -> "The caller was allowed, but the business refuses it."
                is Unserved -> "The system can't serve it right now, though nothing was wrong with the request."
            }

    /** See [Failed.groupDescription] for this group's definition. */
    data class Restricted(
        override val name: String,
        override val message: String,
        override val origin: String = StatusConstants.CUSTOM,
        override val scope: String = "",
    ) : Failed() {
        companion object {
            @JvmField
            @JsStatic
            val DENIED =
                Restricted(
                    "DENIED",
                    "The request was denied.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            @JsStatic
            val UNAUTHENTICATED =
                Restricted(
                    "UNAUTHENTICATED",
                    "Authentication is required.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            @JsStatic
            val UNAUTHORIZED =
                Restricted(
                    "UNAUTHORIZED",
                    "The caller lacks permission.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            @JsStatic
            val FORBIDDEN =
                Restricted(
                    "FORBIDDEN",
                    "Access to this resource is forbidden.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            @JsStatic
            val LOCKED =
                Restricted(
                    "LOCKED",
                    "Access is locked; resolve the condition to restore access.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            @JsStatic
            val SUSPENDED =
                Restricted(
                    "SUSPENDED",
                    "Access has been administratively suspended.",
                    origin = StatusConstants.KIIT,
                )
        }
    }

    /** See [Failed.groupDescription] for this group's definition. */
    data class Invalid(
        override val name: String,
        override val message: String,
        override val origin: String = StatusConstants.CUSTOM,
        override val scope: String = "",
    ) : Failed() {
        companion object {
            @JvmField
            @JsStatic
            val INVALID_VALUE =
                Invalid(
                    "INVALID_VALUE",
                    "The request had an invalid value.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            @JsStatic
            val BAD_REQUEST =
                Invalid(
                    "BAD_REQUEST",
                    "The request was malformed.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            @JsStatic
            val NOT_FOUND =
                Invalid(
                    "NOT_FOUND",
                    "The requested route or endpoint does not exist.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            @JsStatic
            val OUT_OF_RANGE =
                Invalid(
                    "OUT_OF_RANGE",
                    "A value was outside the acceptable range.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            @JsStatic
            val PAYLOAD_TOO_LARGE =
                Invalid(
                    "PAYLOAD_TOO_LARGE",
                    "The payload is too large.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            @JsStatic
            val MISSING_FIELD =
                Invalid(
                    "MISSING_FIELD",
                    "A required field was not provided.",
                    origin = StatusConstants.KIIT,
                )
        }
    }

    /** See [Failed.groupDescription] for this group's definition. */
    data class Rejected(
        override val name: String,
        override val message: String,
        override val origin: String = StatusConstants.CUSTOM,
        override val scope: String = "",
    ) : Failed() {
        companion object {
            @JvmField
            @JsStatic
            val RULE_VIOLATION =
                Rejected(
                    "RULE_VIOLATION",
                    "A business rule rejected the request.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            @JsStatic
            val CONFLICT =
                Rejected(
                    "CONFLICT",
                    "The request conflicts with the current state.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            @JsStatic
            val NOT_EXISTS =
                Rejected(
                    "NOT_EXISTS",
                    "The referenced item does not exist.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            @JsStatic
            val PRECONDITION_FAILED =
                Rejected(
                    "PRECONDITION_FAILED",
                    "A required precondition was not met.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            @JsStatic
            val EXPIRED =
                Rejected(
                    "EXPIRED",
                    "The item has expired.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            @JsStatic
            val GONE =
                Rejected(
                    "GONE",
                    "The resource was removed and is no longer available.",
                    origin = StatusConstants.KIIT,
                )
        }
    }

    /**
     * See [Failed.groupDescription] for this group's definition.
     *
     * E.g. capacity, timeout, an unsupported capability, planned maintenance, a degraded or
     * aborted dependency, a legal/regulatory block, or a genuinely unexpected/unhandled failure
     * (see [Unserved.UNEXPECTED]).
     */
    data class Unserved(
        override val name: String,
        override val message: String,
        override val origin: String = StatusConstants.CUSTOM,
        override val scope: String = "",
    ) : Failed() {
        companion object {
            @JvmField
            @JsStatic
            val UNEXPECTED =
                Unserved(
                    "UNEXPECTED",
                    "An unexpected, unclassified error occurred.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            @JsStatic
            val UNSUPPORTED =
                Unserved(
                    "UNSUPPORTED",
                    "This capability is not currently available.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            @JsStatic
            val TIMEOUT =
                Unserved(
                    "TIMEOUT",
                    "The operation timed out.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            @JsStatic
            val RATE_LIMITED =
                Unserved(
                    "RATE_LIMITED",
                    "Too many requests; try again later.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            @JsStatic
            val RESOURCE_LIMITED =
                Unserved(
                    "RESOURCE_LIMITED",
                    "A resource limit has been reached.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            @JsStatic
            val UNREACHABLE =
                Unserved(
                    "UNREACHABLE",
                    "A required dependency could not be reached.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            @JsStatic
            val UNDER_MAINTENANCE =
                Unserved(
                    "UNDER_MAINTENANCE",
                    "The service is temporarily under maintenance.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            @JsStatic
            val INTERNAL =
                Unserved(
                    "INTERNAL",
                    "An internal invariant was violated.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            @JsStatic
            val DATA_LOSS =
                Unserved(
                    "DATA_LOSS",
                    "Unrecoverable data loss or corruption occurred.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            @JsStatic
            val DEGRADED =
                Unserved(
                    "DEGRADED",
                    "This dependency is degraded; some calls may be refused.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            @JsStatic
            val LEGAL_BLOCK =
                Unserved(
                    "LEGAL_BLOCK",
                    "Access is blocked for legal reasons.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            @JsStatic
            val ABORTED =
                Unserved(
                    "ABORTED",
                    "The operation was aborted; retrying may help.",
                    origin = StatusConstants.KIIT,
                )
        }
    }
}

// Package-level shorthands, fully transparent: `Restricted` and `Failed.Restricted` are the same
// type, not a copy, so each alias inherits its target's companion members automatically. They
// exist purely for brevity at call sites, see [Passed] and [Failed] for the real declarations.
typealias Succeeded = Passed.Succeeded
typealias Pending = Passed.Pending
typealias Excluded = Passed.Excluded
typealias Information = Passed.Information

typealias Restricted = Failed.Restricted
typealias Invalid = Failed.Invalid
typealias Rejected = Failed.Rejected
typealias Unserved = Failed.Unserved
