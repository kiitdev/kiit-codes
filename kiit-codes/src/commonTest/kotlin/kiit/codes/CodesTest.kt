package kiit.codes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

// =================================================================================================
// CodesTest: the built-in registry
// =================================================================================================

class CodesTest {
    @Test
    fun successHasCorrectValues() {
        assertEquals("SUCCESS", Succeeded.SUCCESS.name)
        assertEquals(StatusConstants.KIIT, Succeeded.SUCCESS.origin)
        assertEquals("The operation completed successfully.", Succeeded.SUCCESS.message)
        assertTrue(Succeeded.SUCCESS.success)
    }

    @Test
    fun deniedHasCorrectValues() {
        assertEquals("DENIED", Restricted.DENIED.name)
        assertEquals(StatusConstants.KIIT, Restricted.DENIED.origin)
        assertFalse(Restricted.DENIED.success)
    }

    @Test
    fun forbiddenIsRestricted() {
        // Access-control outcome, not a business-rule failure, see Codes.kt for the reasoning.
        assertTrue(Restricted.FORBIDDEN is Failed.Restricted)
    }

    @Test
    fun expiredIsRejected() {
        // Was valid and timed out, a known business outcome, not malformed input.
        assertTrue(Rejected.EXPIRED is Failed.Rejected)
    }

    @Test
    fun goneIsRejected() {
        // Deliberately removed, a known business outcome, not malformed input.
        assertTrue(Rejected.GONE is Failed.Rejected)
    }

    @Test
    fun skippedAndDiscardedHaveDistinctNamesAndSuccessTrue() {
        assertTrue(Excluded.SKIPPED.success)
        assertTrue(Excluded.DISCARDED.success)
        assertTrue(Excluded.SKIPPED.name != Excluded.DISCARDED.name)
    }

    @Test
    fun informationCodesHaveSuccessTrue() {
        assertTrue(Information.METADATA.success)
        assertTrue(Information.HEALTH.success)
        assertTrue(Information.DIAGNOSTICS.success)
    }

    @Test
    fun exitedIsSucceededNotInformation() {
        // Exiting is a real completed operation, not metadata, moved from Information.EXIT.
        assertTrue(Succeeded.EXITED.success)
        assertTrue(Succeeded.EXITED is Passed.Succeeded)
    }

    @Test
    fun scheduledIsPending() {
        assertTrue(Pending.SCHEDULED.success)
        assertTrue(Pending.SCHEDULED is Passed.Pending)
    }

    @Test
    fun disqualifiedIsExcluded() {
        // Evaluated, and the item's own state/attributes didn't meet criteria.
        assertTrue(Excluded.DISQUALIFIED.success)
        assertTrue(Excluded.DISQUALIFIED is Passed.Excluded)
    }

    @Test
    fun degradedLegalBlockAndAbortedAreUnserved() {
        assertFalse(Unserved.DEGRADED.success)
        assertFalse(Unserved.LEGAL_BLOCK.success)
        assertFalse(Unserved.ABORTED.success)
        assertTrue(Unserved.DEGRADED is Failed.Unserved)
        assertTrue(Unserved.LEGAL_BLOCK is Failed.Unserved)
        assertTrue(Unserved.ABORTED is Failed.Unserved)
    }

    @Test
    fun everyBuiltInCodeHasKiitOrigin() {
        assertTrue(Codes.all.all { it.origin == StatusConstants.KIIT })
    }

    @Test
    fun everyBuiltInCodeIsUniqueByOriginAndName() {
        val keys = Codes.all.map { it.origin to it.name }
        assertEquals(keys.size, keys.toSet().size)
    }
}

// =================================================================================================
// CodesToHttpTest / CompositeLookupTest: CodeLookup implementations
// =================================================================================================

class CodesToHttpTest {
    private val http = CodesToHttp()

    // -------------------------------------------------------------------------
    // toCode: group defaults
    // -------------------------------------------------------------------------

    @Test fun groupDefaultSucceeded() {
        assertEquals(200, http.toCode(Succeeded.SUCCESS))
        assertEquals(200, http.toCode(Succeeded.UPDATED))
    }

    @Test fun groupDefaultPending() {
        assertEquals(202, http.toCode(Pending.PROCESSING))
        assertEquals(202, http.toCode(Pending.QUEUED))
        assertEquals(202, http.toCode(Pending.ACCEPTED))
    }

    @Test fun groupDefaultExcluded() {
        assertEquals(200, http.toCode(Excluded.OMITTED))
        assertEquals(200, http.toCode(Excluded.SKIPPED))
        assertEquals(200, http.toCode(Excluded.DISCARDED))
    }

    @Test fun groupDefaultInformation() {
        assertEquals(200, http.toCode(Information.METADATA))
    }

    @Test fun groupDefaultRestricted() {
        assertEquals(401, http.toCode(Restricted.DENIED))
        assertEquals(401, http.toCode(Restricted.UNAUTHENTICATED))
    }

    @Test fun groupDefaultInvalid() {
        assertEquals(400, http.toCode(Invalid.BAD_REQUEST))
        assertEquals(400, http.toCode(Invalid.INVALID_VALUE))
        assertEquals(400, http.toCode(Invalid.OUT_OF_RANGE))
    }

    @Test fun groupDefaultRejected() {
        assertEquals(409, http.toCode(Rejected.RULE_VIOLATION))
        // CONFLICT needs no override, 409 is already the group default it would've resolved to.
        assertEquals(409, http.toCode(Rejected.CONFLICT))
        assertEquals(409, http.toCode(Rejected.PRECONDITION_FAILED))
    }

    @Test fun groupDefaultUnserved() {
        assertEquals(503, http.toCode(Unserved.UNREACHABLE))
        assertEquals(503, http.toCode(Unserved.UNDER_MAINTENANCE))
    }

    // -------------------------------------------------------------------------
    // toCode: per-code overrides
    // -------------------------------------------------------------------------

    @Test fun overrideCreated() {
        assertEquals(201, http.toCode(Succeeded.CREATED))
    }

    @Test fun overrideHandled() {
        assertEquals(204, http.toCode(Succeeded.HANDLED))
    }

    @Test fun overrideNotFound() {
        assertEquals(404, http.toCode(Invalid.NOT_FOUND))
    }

    @Test fun overrideForbidden() {
        assertEquals(403, http.toCode(Restricted.FORBIDDEN))
    }

    @Test fun overrideExpired() {
        assertEquals(410, http.toCode(Rejected.EXPIRED))
    }

    @Test fun overrideGone() {
        assertEquals(410, http.toCode(Rejected.GONE))
    }

    @Test fun overrideNotExists() {
        assertEquals(404, http.toCode(Rejected.NOT_EXISTS))
    }

    @Test fun overrideCancelled() {
        assertEquals(499, http.toCode(Excluded.CANCELLED))
    }

    @Test fun overrideRedirected() {
        assertEquals(307, http.toCode(Pending.REDIRECTED))
    }

    @Test fun overridePayloadTooLarge() {
        assertEquals(413, http.toCode(Invalid.PAYLOAD_TOO_LARGE))
    }

    @Test fun overrideLocked() {
        assertEquals(423, http.toCode(Restricted.LOCKED))
    }

    @Test fun overrideSuspended() {
        assertEquals(403, http.toCode(Restricted.SUSPENDED))
    }

    @Test fun overrideResourceLimited() {
        assertEquals(429, http.toCode(Unserved.RESOURCE_LIMITED))
    }

    @Test fun overrideTimeout() {
        assertEquals(504, http.toCode(Unserved.TIMEOUT))
    }

    @Test fun overrideRateLimited() {
        assertEquals(429, http.toCode(Unserved.RATE_LIMITED))
    }

    @Test fun overrideUnexpected() {
        assertEquals(500, http.toCode(Unserved.UNEXPECTED))
    }

    @Test fun overrideLegalBlock() {
        assertEquals(451, http.toCode(Unserved.LEGAL_BLOCK))
    }

    @Test fun groupDefaultCoversDegradedAndAborted() {
        // Neither has a closer HTTP equivalent, deliberately fall through to Unserved's default.
        assertEquals(503, http.toCode(Unserved.DEGRADED))
        assertEquals(503, http.toCode(Unserved.ABORTED))
    }

    /** A custom, unregistered status still resolves via its group's default, not a guessed fallback. */
    @Test
    fun toCodeFallsBackToGroupDefaultForCustomStatus() {
        val custom = Failed.Rejected("CUSTOM", "Custom error")
        assertEquals(409, http.toCode(custom)) // Rejected's group default
    }

    /**
     * [overrides] is keyed by [Status.id] (origin+group+name), not full structural equality. A
     * status sharing NOT_FOUND's identity but a different message still resolves to its
     * override, since [Status] being a data class would otherwise compare every field including
     * [Status.message].
     */
    @Test
    fun overrideMatchesByIdentityNotFullStatusEquality() {
        val differentMessage = Failed.Invalid("NOT_FOUND", "A completely different message.", origin = StatusConstants.KIIT)
        assertEquals(404, http.toCode(differentMessage))
    }

    /**
     * A custom status can share a built-in override's origin+name while belonging to a
     * completely different group, e.g. this shares [Succeeded.CREATED]'s origin ("kiit") and
     * name ("CREATED") even though it's a [Failed.Invalid]. Since [Status.id] includes group,
     * the two don't actually share an id, so this must still resolve to Invalid's own group
     * default, not CREATED's 201.
     */
    @Test
    fun overrideDoesNotApplyAcrossDifferentGroupsWithSameOriginAndName() {
        val collidesWithCreated = Failed.Invalid("CREATED", "failure", origin = StatusConstants.KIIT)
        assertEquals(400, http.toCode(collidesWithCreated))
    }

    /** Same cross-group origin+name collision in the other direction, against a different overridden code. */
    @Test
    fun overrideDoesNotApplyAcrossDifferentGroupsWithSameOriginAndNameReverse() {
        val collidesWithNotFound = Succeeded("NOT_FOUND", "ok", origin = StatusConstants.KIIT)
        assertEquals(200, http.toCode(collidesWithNotFound))
    }

    // -------------------------------------------------------------------------
    // toStatus: reverse lookup, derived from toCode
    // -------------------------------------------------------------------------

    @Test
    fun toStatusFindsRegisteredStatusForUniqueHttpCode() {
        val status = http.toStatus(201)
        assertNotNull(status)
        assertEquals(Succeeded.CREATED.name, status.name)
    }

    @Test
    fun toStatusReturnsNullForUnrecognizedHttpCode() {
        // No guessed range fallback, an unrecognized code is honestly null, caller decides the default.
        assertNull(http.toStatus(999))
    }

    @Test
    fun toStatusRoundTripsForOverriddenCode() {
        val status = http.toStatus(404)
        assertNotNull(status)
        assertEquals(Invalid.NOT_FOUND.name, status.name)
    }

    // -------------------------------------------------------------------------
    // toStatus: deterministic canonical choice for codes shared by multiple statuses
    // -------------------------------------------------------------------------

    /**
     * Spells out the lossy round trip directly: converting UPDATED forward and back doesn't
     * return UPDATED. toStatus(toCode(x)) == x does not generally hold, see toStatus's doc.
     */
    @Test
    fun httpRoundTripDoesNotPreserveTheOriginalStatus() {
        val original = Succeeded.UPDATED
        val code = http.toCode(original)
        val restored = http.toStatus(code)

        assertEquals(200, code)
        assertSame(Succeeded.SUCCESS, restored)
        assertNotEquals<Status?>(original, restored)
    }

    /**
     * Many built-in statuses resolve to 200. Pins the canonical winner so this can't silently
     * change if [Codes.all]'s declaration order ever shifts.
     */
    @Test
    fun toStatus200ResolvesToSuccessNotOtherSharedStatuses() {
        assertSame(Succeeded.SUCCESS, http.toStatus(200))
    }

    /** NOT_FOUND and NOT_EXISTS both resolve to 404; NOT_FOUND wins. */
    @Test
    fun toStatus404ResolvesToNotFoundNotNotExists() {
        assertSame(Invalid.NOT_FOUND, http.toStatus(404))
    }

    /** EXPIRED and GONE both resolve to 410; GONE wins, its name and message are the literal HTTP 410 concept. */
    @Test
    fun toStatus410ResolvesToGoneNotExpired() {
        assertSame(Rejected.GONE, http.toStatus(410))
    }

    /** Only UNEXPECTED resolves to 500 now that Rejected's default moved to 409, no tie to break. */
    @Test
    fun toStatus500ResolvesToUnexpected() {
        assertSame(Unserved.UNEXPECTED, http.toStatus(500))
    }

    /** RULE_VIOLATION, CONFLICT, and PRECONDITION_FAILED all resolve to 409; CONFLICT wins. */
    @Test
    fun toStatus409ResolvesToConflictNotRuleViolationOrPreconditionFailed() {
        assertSame(Rejected.CONFLICT, http.toStatus(409))
    }

    /** Only UNSUPPORTED resolves to 501 now that UNIMPLEMENTED was removed, no tie to break. */
    @Test
    fun toStatus501ResolvesToUnsupported() {
        assertSame(Unserved.UNSUPPORTED, http.toStatus(501))
    }

    /**
     * `INVALID_ENTITY` was removed from the registry, so `422` has no dedicated [Status] mapping
     * again. This is a known, accepted regression, not a bug. Anything converting
     * [Invalid.INVALID_VALUE] to HTTP falls through to its group default, 400, same as before.
     */
    @Test
    fun toStatus422ReturnsNullSinceInvalidEntityWasRemoved() {
        assertNull(http.toStatus(422))
    }

    /** DENIED, UNAUTHENTICATED, and UNAUTHORIZED all resolve to 401; UNAUTHENTICATED wins. */
    @Test
    fun toStatus401ResolvesToUnauthenticatedNotDeniedOrUnauthorized() {
        assertSame(Restricted.UNAUTHENTICATED, http.toStatus(401))
    }

    /** FORBIDDEN and SUSPENDED both resolve to 403; FORBIDDEN wins. */
    @Test
    fun toStatus403ResolvesToForbiddenNotSuspended() {
        assertSame(Restricted.FORBIDDEN, http.toStatus(403))
    }

    /**
     * The canonical tie-breaking is still derived from this instance's own [CodesToHttp.toCode],
     * not a fixed table, a custom [overrides] map changes both directions together.
     */
    @Test
    fun toStatusStaysInSyncWithCustomOverridesNotJustDefaults() {
        val custom = CodesToHttp(overrides = mapOf(Unserved.TIMEOUT.id to 599))
        assertSame(Unserved.TIMEOUT, custom.toStatus(599))
        assertNull(custom.toStatus(504)) // TIMEOUT no longer resolves to 504 for this instance
    }
}

// =================================================================================================
// CodesToGrpcTest
// =================================================================================================

class CodesToGrpcTest {
    private val grpc = CodesToGrpc()

    @Test fun groupDefaultPassedIsOk() {
        assertEquals(0, grpc.toCode(Succeeded.SUCCESS))
        assertEquals(0, grpc.toCode(Pending.PROCESSING))
        assertEquals(0, grpc.toCode(Excluded.SKIPPED))
        assertEquals(0, grpc.toCode(Information.METADATA))
    }

    @Test fun groupDefaultRestrictedIsPermissionDenied() {
        assertEquals(7, grpc.toCode(Restricted.DENIED))
        assertEquals(7, grpc.toCode(Restricted.UNAUTHORIZED))
        assertEquals(7, grpc.toCode(Restricted.FORBIDDEN))
    }

    @Test fun groupDefaultInvalidIsInvalidArgument() {
        assertEquals(3, grpc.toCode(Invalid.BAD_REQUEST))
        assertEquals(3, grpc.toCode(Invalid.MISSING_FIELD))
    }

    @Test fun groupDefaultRejectedIsFailedPrecondition() {
        assertEquals(9, grpc.toCode(Rejected.RULE_VIOLATION))
        assertEquals(9, grpc.toCode(Rejected.NOT_EXISTS))
        assertEquals(9, grpc.toCode(Rejected.EXPIRED))
        assertEquals(9, grpc.toCode(Rejected.GONE))
    }

    @Test fun groupDefaultUnservedIsInternal() {
        // UNSUPPORTED is overridden to 12 (see overrideUnsupported) and no longer falls here.
        assertEquals(13, grpc.toCode(Unserved.UNDER_MAINTENANCE))
        // No closer gRPC equivalent for either, deliberately fall through to the group default.
        assertEquals(13, grpc.toCode(Unserved.DEGRADED))
        assertEquals(13, grpc.toCode(Unserved.LEGAL_BLOCK))
    }

    @Test fun overrideCancelled() {
        assertEquals(1, grpc.toCode(Excluded.CANCELLED))
    }

    @Test fun overrideUnauthenticated() {
        assertEquals(16, grpc.toCode(Restricted.UNAUTHENTICATED))
    }

    @Test fun overrideNotFound() {
        assertEquals(5, grpc.toCode(Invalid.NOT_FOUND))
    }

    @Test fun overrideOutOfRange() {
        assertEquals(11, grpc.toCode(Invalid.OUT_OF_RANGE))
    }

    @Test fun overrideConflict() {
        assertEquals(6, grpc.toCode(Rejected.CONFLICT))
    }

    /** Takes over gRPC's UNIMPLEMENTED (12) slot now that UNIMPLEMENTED and UNSUPPORTED merged. */
    @Test fun overrideUnsupported() {
        assertEquals(12, grpc.toCode(Unserved.UNSUPPORTED))
    }

    @Test fun overrideUnreachable() {
        assertEquals(14, grpc.toCode(Unserved.UNREACHABLE))
    }

    @Test fun overrideTimeout() {
        assertEquals(4, grpc.toCode(Unserved.TIMEOUT))
    }

    @Test fun overrideRateLimited() {
        assertEquals(8, grpc.toCode(Unserved.RATE_LIMITED))
    }

    @Test fun overrideUnexpected() {
        assertEquals(2, grpc.toCode(Unserved.UNEXPECTED))
    }

    @Test fun overrideDataLoss() {
        assertEquals(15, grpc.toCode(Unserved.DATA_LOSS))
    }

    /** Exact match, closes what used to be an honest null gap at gRPC's own ABORTED (10). */
    @Test fun overrideAborted() {
        assertEquals(10, grpc.toCode(Unserved.ABORTED))
    }

    /** Not an official gRPC mapping, shares RESOURCE_EXHAUSTED (8) with RATE_LIMITED deliberately. */
    @Test
    fun overridePayloadTooLargeSharesResourceExhausted() {
        assertEquals(8, grpc.toCode(Invalid.PAYLOAD_TOO_LARGE))
    }

    /** Genuine sibling of RATE_LIMITED, same axis, shares RESOURCE_EXHAUSTED (8) too. */
    @Test
    fun overrideResourceLimited() {
        assertEquals(8, grpc.toCode(Unserved.RESOURCE_LIMITED))
    }

    /**
     * A custom status can share a built-in override's origin+name while belonging to a different
     * group, e.g. this shares [Invalid.NOT_FOUND]'s origin ("kiit") and name ("NOT_FOUND")
     * (overridden to 5) even though it's a [Passed.Succeeded]. Since [Status.id] includes group,
     * the two don't actually share an id, so this must still resolve to Passed's own group
     * default (0), not NOT_FOUND's 5.
     */
    @Test
    fun overrideDoesNotApplyAcrossDifferentGroupsWithSameOriginAndName() {
        val collidesWithNotFound = Succeeded("NOT_FOUND", "ok", origin = StatusConstants.KIIT)
        assertEquals(0, grpc.toCode(collidesWithNotFound))
    }

    /** Same cross-group origin+name collision in the other direction, against a different overridden code. */
    @Test
    fun overrideDoesNotApplyAcrossDifferentGroupsWithSameOriginAndNameReverse() {
        val collidesWithCancelled = Failed.Rejected("CANCELLED", "failure", origin = StatusConstants.KIIT)
        assertEquals(9, grpc.toCode(collidesWithCancelled))
    }

    // -------------------------------------------------------------------------
    // toStatus: deterministic canonical choice for gRPC codes shared by multiple statuses
    // -------------------------------------------------------------------------

    /** Every non-overridden Passed status resolves to 0 (OK); SUCCESS wins. */
    @Test
    fun toStatus0ResolvesToSuccess() {
        assertSame(Succeeded.SUCCESS, grpc.toStatus(0))
    }

    /** BAD_REQUEST, INVALID_VALUE, and MISSING_FIELD all resolve to 3; INVALID_VALUE wins. */
    @Test
    fun toStatus3ResolvesToInvalidValue() {
        assertSame(Invalid.INVALID_VALUE, grpc.toStatus(3))
    }

    /** ALREADY_EXISTS: only CONFLICT resolves to 6, no tie to break. */
    @Test
    fun toStatus6ResolvesToConflict() {
        assertSame(Rejected.CONFLICT, grpc.toStatus(6))
    }

    /** DENIED, UNAUTHORIZED, and FORBIDDEN all resolve to 7; DENIED wins. */
    @Test
    fun toStatus7ResolvesToDenied() {
        assertSame(Restricted.DENIED, grpc.toStatus(7))
    }

    /** RATE_LIMITED, PAYLOAD_TOO_LARGE, and RESOURCE_LIMITED all resolve to 8; RATE_LIMITED wins. */
    @Test
    fun toStatus8ResolvesToRateLimitedNotPayloadTooLargeOrResourceLimited() {
        assertSame(Unserved.RATE_LIMITED, grpc.toStatus(8))
    }

    /** RULE_VIOLATION, NOT_EXISTS, PRECONDITION_FAILED, EXPIRED, and GONE all resolve to 9; PRECONDITION_FAILED wins. */
    @Test
    fun toStatus9ResolvesToPreconditionFailed() {
        assertSame(Rejected.PRECONDITION_FAILED, grpc.toStatus(9))
    }

    /** UNDER_MAINTENANCE and INTERNAL both resolve to 13; INTERNAL wins. */
    @Test
    fun toStatus13ResolvesToInternal() {
        assertSame(Unserved.INTERNAL, grpc.toStatus(13))
    }

    /**
     * Only UNSUPPORTED resolves to 12, it took over gRPC's UNIMPLEMENTED slot now that the two
     * built-in concepts merged into one Status code. No tie to break.
     */
    @Test
    fun toStatus12ResolvesToUnsupported() {
        assertSame(Unserved.UNSUPPORTED, grpc.toStatus(12))
    }

    /** ABORTED now maps directly to gRPC 10, closing what used to be an honest null gap. */
    @Test
    fun toStatus10ResolvesToAborted() {
        assertSame(Unserved.ABORTED, grpc.toStatus(10))
    }

    @Test
    fun toStatusReturnsNullForUnrecognizedGrpcCode() {
        assertNull(grpc.toStatus(999))
    }

    @Test
    fun toStatusStaysInSyncWithCustomOverridesNotJustDefaults() {
        val custom = CodesToGrpc(overrides = mapOf(Unserved.TIMEOUT.id to 99))
        assertSame(Unserved.TIMEOUT, custom.toStatus(99))
        assertNull(custom.toStatus(4)) // TIMEOUT no longer resolves to 4 for this instance
    }
}

class CompositeLookupTest {
    private val customCode = Failed.Rejected("PAYMENT_DECLINED", "Payment declined")
    private val lookup = CompositeLookup(base = CodesToHttp(), extensions = mapOf(customCode to 402))

    @Test
    fun extensionTakesPrecedenceForToCode() {
        assertEquals(402, lookup.toCode(customCode))
    }

    @Test
    fun extensionSupportsReverseLookup() {
        val status = lookup.toStatus(402)
        assertNotNull(status)
        assertSame(customCode, status)
    }

    @Test
    fun fallsBackToBaseForRegisteredCodes() {
        assertEquals(401, lookup.toCode(Restricted.DENIED))
        assertSame(Succeeded.CREATED, lookup.toStatus(201))
    }

    @Test
    fun fallsBackToBaseNullWhenNeitherKnows() {
        assertNull(lookup.toStatus(999))
    }

    /**
     * [CompositeLookup.toCode] matches by [Status.origin]/[Status.name], not full [Status]
     * equality. A status sharing [customCode]'s identity but a different message still resolves
     * to its extension.
     */
    @Test
    fun extensionMatchesByIdentityNotFullStatusEquality() {
        val differentMessage = Failed.Rejected("PAYMENT_DECLINED", "A completely different message.", origin = customCode.origin)
        assertEquals(402, lookup.toCode(differentMessage))
    }
}
