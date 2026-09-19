/** url: www.kiit.dev */
package kiit.codes

import kotlin.jvm.JvmField

/** Well-known [Status.origin] values. */
object StatusConstants {
    /**
     * Origin for every built-in [Codes] entry. It is kiit's own domain, but [kiit.codes.formats.ProblemConverter]
     * doesn't build `https://kiit.dev/problems/...` for it, it points at kiit-codes' own docs instead.
     */
    const val KIIT = "kiit.dev"

    /**
     * Default origin for consumer/custom statuses that don't specify one explicitly. With no `baseUrls`
     * entry, [kiit.codes.formats.ProblemConverter] would build `https://custom/problems/...` for it, so
     * custom statuses that reach RFC 9457 output should set their own origin.
     */
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
 *      "origin"  : "kiit.dev",
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
     *
     * 1. It is either a real domain (`"stripe.com"`) or any other id (`"myapp1"`).
     * 2. [kiit.codes.formats.ProblemConverter] lowercases it. With no `baseUrls` entry it builds the RFC 9457
     *    `type` as `https://{origin}/problems/...`. The origin is not validated.
     * 3. Nothing stops two consumers from choosing the same non-domain id, and kiit-codes can't detect it.
     *    Pick a specific name when there is no domain. A real domain is unique through DNS.
     */
    val origin: String

    /**
     * Optional, free-form internal-organization label: a department, product area, route, or
     * anything else the consumer wants to attach, e.g. `"payments"`, `"payments.cards"`.
     * `kiit-codes` never parses or enforces [scope]'s internal shape, only that it doesn't
     * contain `:` (reserved, see [path]).
     *
     * Empty string means unset. It defaults to `""` on every built-in and on any
     * [Passed]/[Failed] subtype that doesn't set it explicitly. This is a real, defaulted field
     * rather than a separate capability interface, since consumers construct concrete
     * [Passed]/[Failed] subtypes directly (or `.copy()` an existing instance) and can't add an
     * interface to one after the fact.
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

    /**
     * True if this is its group's built-in default (e.g. [Failed.Invalid.INVALID_VALUE] for
     * `Invalid`), the same instance as that group's `DEFAULT`. Compared by value, so a `copy()`
     * that changes any field, `message` included, is not the default.
     */
    val isDefault: Boolean
}

/**
 * Module-internal identity, replacing the old `"$origin.$group.$name"` string key. A real data
 * class gets correct `equals`/`hashCode` for free, and now includes [scope] too, so a [Status]
 * with a non-empty [Status.scope] won't collide with one that only matches on
 * [origin]/[group]/[name]. Never public, never serialized, only used by [Codes]' own registry
 * lookup.
 *
 * [CodesToHttp]/[CodesToGrpc]'s `overrides` maps stay `Map<String, Int>` since that constructor
 * signature is already published and this release can't break it. They use [Status.key] instead,
 * a string built from the same fields as this class.
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
 * String form of [statusKey], for [CodesToHttp]/[CodesToGrpc]'s `overrides` maps. Their
 * constructor and `DEFAULT_OVERRIDES` are public, so they can't declare an `internal`-typed key
 * (Kotlin won't let a public signature expose an internal type) without breaking an
 * already-published API. Same collision safety as [StatusKey] otherwise: scoped by
 * origin+scope+group+name, not just origin+group+name like the old `id` this replaces.
 */
internal val Status.key: String
    get() = statusKey.let { "${it.origin}:${it.scope}:${it.group}:${it.name}" }

/**
 * Public, display-oriented, `:`-delimited identity for logging and debugging. Not for lookups
 * or comparison, since [Status.scope] is consumer-defined and can change over time. See [code]
 * for a value that's actually safe to compare across releases.
 *
 * Feeds into [toProblemDetail]'s RFC 9457 `type` construction by default (see
 * [defaultTypeBuilder]). If you rely on `type` staying stable, [Status.origin]/[Status.scope]
 * inherit that same obligation: they're consumer-defined and only as stable as you keep them.
 */
val Status.path: String
    get() = if (scope.isNotEmpty()) "$origin:$scope" else origin

/**
 * Public, `:`-delimited identity built entirely from hard-rule, compiler-enforced fields
 * ([success], [group], [name]). Safe to log, diff, or compare across releases, unlike [path].
 *
 * Not unique. Two [Status]es can share the same [code] while differing in [Status.origin]/
 * [Status.scope] (e.g. two different consumers both defining a `Failed.Rejected("CONFLICT", ...)`).
 * Do not use [code] for identity, lookup, or equality checks. Compare fields directly, or use
 * the internal `StatusKey` (origin+scope+group+name) if you're inside this module.
 */
val Status.code: String
    get() = "${if (success) "Passed" else "Failed"}:$group:$name"

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

    final override val isDefault: Boolean
        get() =
            when (this) {
                is Succeeded -> this == Succeeded.DEFAULT
                is Pending -> this == Pending.DEFAULT
                is Excluded -> this == Excluded.DEFAULT
                is Information -> this == Information.DEFAULT
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
            val SUCCESS =
                Succeeded(
                    "SUCCESS",
                    "The operation completed successfully.",
                    origin = StatusConstants.KIIT,
                )

            /** This group's default. Same instance as [SUCCESS], not a new entry. */
            @JvmField
            val DEFAULT = SUCCESS

            @JvmField
            val CREATED =
                Succeeded(
                    "CREATED",
                    "A new resource was created.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            val UPDATED =
                Succeeded(
                    "UPDATED",
                    "The resource was fully updated.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            val PATCHED =
                Succeeded(
                    "PATCHED",
                    "The resource was partially updated.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            val FETCHED =
                Succeeded(
                    "FETCHED",
                    "The resource was retrieved.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            val DELETED =
                Succeeded(
                    "DELETED",
                    "The resource was deleted.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            val HANDLED =
                Succeeded(
                    "HANDLED",
                    "The request was handled; nothing to return.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            val REFERRED =
                Succeeded(
                    "REFERRED",
                    "The result is at another location.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
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
            val ACCEPTED =
                Pending(
                    "ACCEPTED",
                    "The request was accepted.",
                    origin = StatusConstants.KIIT,
                )

            /** This group's default. Same instance as [ACCEPTED], not a new entry. */
            @JvmField
            val DEFAULT = ACCEPTED

            @JvmField
            val QUEUED =
                Pending(
                    "QUEUED",
                    "The request is waiting to be processed.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            val PROCESSING =
                Pending(
                    "PROCESSING",
                    "The request is being processed.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            val CONFIRM =
                Pending(
                    "CONFIRM",
                    "The request is awaiting confirmation.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            val REDIRECTED =
                Pending(
                    "REDIRECTED",
                    "This request is being handled elsewhere.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
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
            val OMITTED =
                Excluded(
                    "OMITTED",
                    "The item was excluded from the result.",
                    origin = StatusConstants.KIIT,
                )

            /** This group's default. Same instance as [OMITTED], not a new entry. */
            @JvmField
            val DEFAULT = OMITTED

            @JvmField
            val SKIPPED =
                Excluded(
                    "SKIPPED",
                    "The item was not processed.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            val DISCARDED =
                Excluded(
                    "DISCARDED",
                    "The item was processed, then excluded for unrelated reasons.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            val CANCELLED =
                Excluded(
                    "CANCELLED",
                    "The operation was cancelled by the caller before completion.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            val DEDUPLICATED =
                Excluded(
                    "DEDUPLICATED",
                    "The duplicate item was not processed.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
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
            val NOTICE =
                Information(
                    "NOTICE",
                    "An informational notice.",
                    origin = StatusConstants.KIIT,
                )

            /** This group's default. Same instance as [NOTICE], not a new entry. */
            @JvmField
            val DEFAULT = NOTICE

            @JvmField
            val ADVISORY =
                Information(
                    "ADVISORY",
                    "A notice that may need attention.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            val METADATA =
                Information(
                    "METADATA",
                    "Information about the application itself was returned.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            val HEALTH =
                Information(
                    "HEALTH",
                    "The service is healthy and operational.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            val DIAGNOSTICS =
                Information(
                    "DIAGNOSTICS",
                    "Diagnostic or operational information was returned.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
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

    final override val isDefault: Boolean
        get() =
            when (this) {
                is Restricted -> this == Restricted.DEFAULT
                is Invalid -> this == Invalid.DEFAULT
                is Rejected -> this == Rejected.DEFAULT
                is Unserved -> this == Unserved.DEFAULT
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
            val DENIED =
                Restricted(
                    "DENIED",
                    "The request was denied.",
                    origin = StatusConstants.KIIT,
                )

            /** This group's default. Same instance as [DENIED], not a new entry. */
            @JvmField
            val DEFAULT = DENIED

            @JvmField
            val UNAUTHENTICATED =
                Restricted(
                    "UNAUTHENTICATED",
                    "Authentication is required.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            val UNAUTHORIZED =
                Restricted(
                    "UNAUTHORIZED",
                    "The caller lacks permission.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            val FORBIDDEN =
                Restricted(
                    "FORBIDDEN",
                    "Access to this resource is forbidden.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            val LOCKED =
                Restricted(
                    "LOCKED",
                    "Access is locked; resolve the condition to restore access.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
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
            val INVALID_VALUE =
                Invalid(
                    "INVALID_VALUE",
                    "The request had an invalid value.",
                    origin = StatusConstants.KIIT,
                )

            /** This group's default. Same instance as [INVALID_VALUE], not a new entry. */
            @JvmField
            val DEFAULT = INVALID_VALUE

            @JvmField
            val BAD_REQUEST =
                Invalid(
                    "BAD_REQUEST",
                    "The request was malformed.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            val NOT_FOUND =
                Invalid(
                    "NOT_FOUND",
                    "The requested route or endpoint does not exist.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            val OUT_OF_RANGE =
                Invalid(
                    "OUT_OF_RANGE",
                    "A value was outside the acceptable range.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            val PAYLOAD_TOO_LARGE =
                Invalid(
                    "PAYLOAD_TOO_LARGE",
                    "The payload is too large.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
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
            val RULE_VIOLATION =
                Rejected(
                    "RULE_VIOLATION",
                    "A business rule rejected the request.",
                    origin = StatusConstants.KIIT,
                )

            /** This group's default. Same instance as [RULE_VIOLATION], not a new entry. */
            @JvmField
            val DEFAULT = RULE_VIOLATION

            @JvmField
            val CONFLICT =
                Rejected(
                    "CONFLICT",
                    "The request conflicts with the current state.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            val NOT_EXISTS =
                Rejected(
                    "NOT_EXISTS",
                    "The referenced item does not exist.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            val PRECONDITION_FAILED =
                Rejected(
                    "PRECONDITION_FAILED",
                    "A required precondition was not met.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            val EXPIRED =
                Rejected(
                    "EXPIRED",
                    "The item has expired.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
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
            val UNEXPECTED =
                Unserved(
                    "UNEXPECTED",
                    "An unexpected, unclassified error occurred.",
                    origin = StatusConstants.KIIT,
                )

            /** This group's default. Same instance as [UNEXPECTED], not a new entry. */
            @JvmField
            val DEFAULT = UNEXPECTED

            @JvmField
            val UNSUPPORTED =
                Unserved(
                    "UNSUPPORTED",
                    "This capability is not currently available.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            val TIMEOUT =
                Unserved(
                    "TIMEOUT",
                    "The operation timed out.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            val RATE_LIMITED =
                Unserved(
                    "RATE_LIMITED",
                    "Too many requests; try again later.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            val RESOURCE_LIMITED =
                Unserved(
                    "RESOURCE_LIMITED",
                    "A resource limit has been reached.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            val UNREACHABLE =
                Unserved(
                    "UNREACHABLE",
                    "A required dependency could not be reached.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            val UNDER_MAINTENANCE =
                Unserved(
                    "UNDER_MAINTENANCE",
                    "The service is temporarily under maintenance.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            val INTERNAL =
                Unserved(
                    "INTERNAL",
                    "An internal invariant was violated.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            val DATA_LOSS =
                Unserved(
                    "DATA_LOSS",
                    "Unrecoverable data loss or corruption occurred.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            val DEGRADED =
                Unserved(
                    "DEGRADED",
                    "This dependency is degraded; some calls may be refused.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
            val LEGAL_BLOCK =
                Unserved(
                    "LEGAL_BLOCK",
                    "Access is blocked for legal reasons.",
                    origin = StatusConstants.KIIT,
                )

            @JvmField
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
