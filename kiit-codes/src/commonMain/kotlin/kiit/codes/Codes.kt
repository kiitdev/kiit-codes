/** url: www.kiit.dev */
@file:OptIn(ExperimentalJsExport::class)

package kiit.codes

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.jvm.JvmField
import kotlin.jvm.JvmOverloads
import kotlin.jvm.JvmStatic

/**
 * Built-in registry of standard [Status] codes covering common operation outcomes.
 *
 * A few things worth knowing about how this registry works:
 * 1. Using it is optional, these are sensible defaults for kiit-result's builder methods. Custom
 *    codes can be created by constructing any [Passed] or [Failed] subtype directly, only the
 *    four groups per type are fixed and closed, see [Status].
 * 2. [Codes] itself declares no constants. Each one lives on its own type's companion object so
 *    IDE autocomplete stays scoped per group. This object is just the aggregate list and
 *    reverse lookup, see [Passed] and [Failed] for the actual values.
 * 3. Uniqueness of every built-in [Status]'s [StatusKey] (origin+scope+group+name) is
 *    enforced at object init time, a collision fails loudly right away instead of surfacing as a
 *    silent wrong lookup later, see [statusFor].
 * 4. [codesAll] and [codesStatusFor] are thin proxy functions for JS/TS callers, since plain
 *    Kotlin `object`s like this one don't export usable static members to JS.
 */
object Codes {
    /** All built-in codes. Used for reverse lookups, see [CodesToHttp], [CompositeLookup]. */
    @JvmField
    val all: List<Status> =
        listOf(
            Succeeded.SUCCESS, Succeeded.CREATED, Succeeded.UPDATED, Succeeded.PATCHED,
            Succeeded.FETCHED, Succeeded.DELETED, Succeeded.HANDLED, Succeeded.REFERRED, Succeeded.EXITED,
            Pending.ACCEPTED, Pending.QUEUED, Pending.PROCESSING, Pending.CONFIRM,
            Pending.REDIRECTED, Pending.SCHEDULED,
            Excluded.OMITTED, Excluded.SKIPPED, Excluded.DISCARDED, Excluded.CANCELLED,
            Excluded.DEDUPLICATED, Excluded.DISQUALIFIED,
            Information.NOTICE, Information.ADVISORY, Information.METADATA, Information.HEALTH,
            Information.DIAGNOSTICS, Information.MOVED,
            Restricted.DENIED, Restricted.UNAUTHENTICATED, Restricted.UNAUTHORIZED,
            Restricted.FORBIDDEN, Restricted.LOCKED, Restricted.SUSPENDED,
            Invalid.INVALID_VALUE, Invalid.BAD_REQUEST, Invalid.NOT_FOUND, Invalid.OUT_OF_RANGE,
            Invalid.PAYLOAD_TOO_LARGE, Invalid.MISSING_FIELD,
            Rejected.RULE_VIOLATION, Rejected.CONFLICT, Rejected.NOT_EXISTS,
            Rejected.PRECONDITION_FAILED, Rejected.EXPIRED, Rejected.GONE,
            Unserved.UNEXPECTED, Unserved.UNSUPPORTED, Unserved.TIMEOUT, Unserved.RATE_LIMITED,
            Unserved.RESOURCE_LIMITED, Unserved.UNREACHABLE, Unserved.UNDER_MAINTENANCE,
            Unserved.INTERNAL, Unserved.DATA_LOSS, Unserved.DEGRADED, Unserved.LEGAL_BLOCK, Unserved.ABORTED,
        )

    private val byKey: Map<StatusKey, Status> = all.associateBy { it.statusKey }

    init {
        check(byKey.size == all.size) {
            val duplicates = all.groupBy { it.statusKey }.filterValues { it.size > 1 }.keys
            "Duplicate Status codes detected in Codes registry: $duplicates"
        }
    }

    /** Looks up a built-in [Status] by its [Status.origin]/[Status.group]/[Status.name], or null if none matches. */
    @JvmStatic
    fun statusFor(origin: String, group: String, name: String): Status? =
        byKey[StatusKey(origin = origin, scope = "", group = group, name = name)]
}

/** JS/TS-reachable proxy for [Codes.all], see [Codes]'s KDoc for why this exists. */
@JsExport
fun codesAll(): List<Status> = Codes.all

/** JS/TS-reachable proxy for [Codes.statusFor], see [Codes]'s KDoc for why this exists. */
@JsExport
fun codesStatusFor(origin: String, group: String, name: String): Status? = Codes.statusFor(origin, group, name)

/**
 * Bidirectional conversion between a [Status] and a target protocol's status code (e.g. HTTP).
 *
 * 1. Implementations should be exhaustive over [Status]'s groups ([Passed]/[Failed]
 *    subtypes), typically via a `when` with no `else` branch, so a newly added group is
 *    caught at compile time.
 * 2. Individual codes within a group don't need an exhaustive mapping. They can be handled
 *    via a small overrides table layered on top of the group default, see [CodesToHttp].
 */
@JsExport
interface CodeLookup {
    /** Converts a [Status] to the target protocol's code. */
    fun toCode(status: Status): Int

    /**
     * Converts a target protocol [code] to a matching [Status], or null if there is no match.
     * The forward direction is typically many-to-one, so this is inherently lossy. It returns
     * *a* status that resolves to [code], not necessarily the specific one a caller originally
     * had in hand.
     */
    fun toStatus(code: Int): Status?
}

/**
 * Default [CodeLookup] implementation mapping [Status] to HTTP status codes.
 *
 * Group -> HTTP default:
 *   Succeeded / Excluded / Information -> 200      Pending -> 202
 *   Restricted -> 401  Invalid -> 400   Rejected -> 409        Unserved -> 503
 *
 * 1. Individual codes can differ from their group's default via [overrides] (e.g. CREATED ->
 *    201, NOT_FOUND -> 404). [toStatus] is derived from [toCode] and is lossy, see its own doc.
 * 2. Clients needing additional or custom codes should compose with [CompositeLookup] rather
 *    than subclassing this type directly, see [CompositeLookup] for why.
 */
@JsExport
open class CodesToHttp
    @JvmOverloads
    constructor(
        private val overrides: Map<String, Int> = DEFAULT_OVERRIDES,
    ) : CodeLookup {
        override fun toCode(status: Status): Int {
            overrides[status.key]?.let { return it }
            return when (status) {
                is Passed.Succeeded -> 200
                is Passed.Pending -> 202
                is Passed.Excluded -> 200
                is Passed.Information -> 200
                is Failed.Restricted -> 401
                is Failed.Invalid -> 400
                is Failed.Rejected -> 409
                is Failed.Unserved -> 503
            }
        }

        /**
         * Reverse lookup, derived from [toCode] so it can't get out of sync with a custom
         * [overrides] map.
         *
         * 1. Lossy by nature since many statuses can share one code.
         * 2. Ties break deterministically via [CANONICAL_PREFERENCE] rather than [Codes.all]'s
         *    plain declaration order.
         * 3. Only finds statuses registered in [Codes], see [CompositeLookup] for custom ones.
         */
        override fun toStatus(code: Int): Status? =
            CANONICAL_PREFERENCE.firstOrNull { toCode(it) == code }
                ?: Codes.all.firstOrNull { toCode(it) == code }

        companion object {
            @JvmField
            val DEFAULT_OVERRIDES: Map<String, Int> =
                mapOf(
                    Succeeded.CREATED.key to 201,
                    Succeeded.HANDLED.key to 204,
                    Pending.CONFIRM.key to 200,
                    Excluded.CANCELLED.key to 499,
                    Pending.REDIRECTED.key to 307,
                    Invalid.NOT_FOUND.key to 404,
                    Rejected.NOT_EXISTS.key to 404,
                    Restricted.FORBIDDEN.key to 403,
                    // closer to Forbidden than Unauthenticated, the caller is known
                    Restricted.SUSPENDED.key to 403,
                    Restricted.LOCKED.key to 423,
                    Rejected.EXPIRED.key to 410,
                    Rejected.GONE.key to 410,
                    // CONFLICT needs no override, 409 is already Rejected's own group default
                    Invalid.PAYLOAD_TOO_LARGE.key to 413,
                    // HTTP has no separate "unsupported" code
                    Unserved.UNSUPPORTED.key to 501,
                    // deadline exceeded waiting on something else, not a slow client (408)
                    Unserved.TIMEOUT.key to 504,
                    Unserved.RATE_LIMITED.key to 429,
                    // same axis as RATE_LIMITED, HTTP doesn't distinguish the two
                    Unserved.RESOURCE_LIMITED.key to 429,
                    Unserved.UNEXPECTED.key to 500,
                    Unserved.LEGAL_BLOCK.key to 451,
                )

            /**
             * 1. One canonical winner per HTTP code that more than one built-in [Status] can resolve to
             *    via [toCode], under [DEFAULT_OVERRIDES] or a group default. See [toStatus].
             * 2. `422 Unprocessable Entity` has no dedicated [Status] mapping. The code that previously held
             *    it, `INVALID_ENTITY`, was removed from the registry. [toStatus] returns null for 422, and
             *    anything converting [Invalid.INVALID_VALUE] to HTTP falls through to 400.
             */
            private val CANONICAL_PREFERENCE: List<Status> =
                listOf(
                    Succeeded.SUCCESS, Succeeded.CREATED, Succeeded.HANDLED, Pending.PROCESSING,
                    Restricted.UNAUTHENTICATED, Restricted.FORBIDDEN,
                    Invalid.INVALID_VALUE, Invalid.NOT_FOUND, Rejected.GONE,
                    // RULE_VIOLATION and PRECONDITION_FAILED also fall through to 409, Rejected's own
                    // group default. CONFLICT wins since it's the most literal match for the concept.
                    Rejected.CONFLICT, Unserved.TIMEOUT, Unserved.RATE_LIMITED,
                    Unserved.UNDER_MAINTENANCE,
                )
        }
    }

/**
 * [CodeLookup] implementation mapping [Status] to gRPC status codes (0-16).
 *
 * Group -> gRPC default: Passed (all) -> 0 (OK)   Restricted -> 7 (PERMISSION_DENIED)
 *   Invalid -> 3 (INVALID_ARGUMENT)   Rejected -> 9 (FAILED_PRECONDITION)   Unserved -> 13 (INTERNAL)
 *
 * gRPC's `ABORTED` (10) maps to [Unserved.ABORTED], previously an honest `null` gap, now closed.
 */
@JsExport
open class CodesToGrpc
    @JvmOverloads
    constructor(
        private val overrides: Map<String, Int> = DEFAULT_OVERRIDES,
    ) : CodeLookup {
        override fun toCode(status: Status): Int {
            overrides[status.key]?.let { return it }
            return when (status) {
                is Passed.Succeeded -> 0
                is Passed.Pending -> 0
                is Passed.Excluded -> 0
                is Passed.Information -> 0
                is Failed.Restricted -> 7
                is Failed.Invalid -> 3
                is Failed.Rejected -> 9
                is Failed.Unserved -> 13
            }
        }

        /**
         * Reverse lookup, derived from [toCode] so it can't get out of sync with a custom
         * [overrides] map. Ties break deterministically via [CANONICAL_PREFERENCE], same approach
         * as [CodesToHttp.toStatus].
         */
        override fun toStatus(code: Int): Status? =
            CANONICAL_PREFERENCE.firstOrNull { toCode(it) == code }
                ?: Codes.all.firstOrNull { toCode(it) == code }

        companion object {
            @JvmField
            val DEFAULT_OVERRIDES: Map<String, Int> =
                mapOf(
                    Excluded.CANCELLED.key to 1,
                    Restricted.UNAUTHENTICATED.key to 16,
                    Invalid.INVALID_VALUE.key to 3,
                    Invalid.NOT_FOUND.key to 5,
                    Invalid.OUT_OF_RANGE.key to 11,
                    Restricted.DENIED.key to 7,
                    // ALREADY_EXISTS, was previously falling through to Rejected's group default
                    Rejected.CONFLICT.key to 6,
                    Rejected.PRECONDITION_FAILED.key to 9,
                    // takes over gRPC's UNIMPLEMENTED slot now that UNIMPLEMENTED and UNSUPPORTED merged
                    // into one Status code
                    Unserved.UNSUPPORTED.key to 12,
                    Unserved.UNREACHABLE.key to 14,
                    Unserved.TIMEOUT.key to 4,
                    Unserved.RATE_LIMITED.key to 8,
                    // RESOURCE_EXHAUSTED, same axis as RATE_LIMITED
                    Unserved.RESOURCE_LIMITED.key to 8,
                    Unserved.UNEXPECTED.key to 2,
                    Unserved.INTERNAL.key to 13,
                    Unserved.DATA_LOSS.key to 15,
                    // RESOURCE_EXHAUSTED, a widely used real-world convention, not an official mapping
                    Invalid.PAYLOAD_TOO_LARGE.key to 8,
                    // exact match, closes the previously honest null gap at 10
                    Unserved.ABORTED.key to 10,
                    // DEGRADED and LEGAL_BLOCK have no closer gRPC equivalent, so they fall through
                    // to Unserved's own group default (13, INTERNAL)
                )

            /** One canonical winner per gRPC code with more than one resolving [Status], see [toStatus]. */
            private val CANONICAL_PREFERENCE: List<Status> =
                listOf(
                    Succeeded.SUCCESS,
                    Invalid.INVALID_VALUE,
                    Restricted.DENIED,
                    Unserved.RATE_LIMITED,
                    Rejected.PRECONDITION_FAILED,
                    Unserved.INTERNAL,
                )
        }
    }

/**
 * Composes a [base] [CodeLookup] with client-supplied [extensions], without modifying or
 * subclassing the base implementation. [extensions] take precedence over [base] in both
 * directions.
 *
 * A couple of details worth knowing:
 * 1. [extensions] is keyed by the actual [Status] instance, so [toStatus] can hand back the
 *    specific custom instance for statuses outside the [Codes.all] registry. There's no other
 *    place to recover it from.
 * 2. [toCode]'s forward lookup matches on [Status.origin]/[Status.name] directly rather than
 *    [Map]'s built-in `equals`/`hashCode`-based `[]` access, since [Status] is a data class that
 *    compares every field. A status with the same origin/name but a different [Status.message]
 *    would otherwise miss the override.
 *
 * ```kotlin
 * val MY_DOMAIN_CODE = Failed.Rejected("PAYMENT_DECLINED", "Payment declined")
 * val lookup = CompositeLookup(CodesToHttp(), mapOf(MY_DOMAIN_CODE to 402))
 * ```
 */
@JsExport
class CompositeLookup(
    private val base: CodeLookup,
    private val extensions: Map<Status, Int>,
) : CodeLookup {
    override fun toCode(status: Status): Int =
        extensions.entries.firstOrNull { it.key.origin == status.origin && it.key.name == status.name }?.value
            ?: base.toCode(status)

    override fun toStatus(code: Int): Status? {
        val extended = extensions.entries.firstOrNull { it.value == code }?.key
        return extended ?: base.toStatus(code)
    }
}
