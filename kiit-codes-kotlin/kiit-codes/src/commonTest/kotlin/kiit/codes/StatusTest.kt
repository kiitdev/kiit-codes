package kiit.codes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

// =================================================================================================
// StatusTest: Passed/Failed subtypes, ofStatus companion function
// =================================================================================================

class StatusTest {
    // -------------------------------------------------------------------------
    // success flag: Passed subtypes (hoisted onto Passed itself, see Passed.success)
    // -------------------------------------------------------------------------

    @Test fun succeededHasSuccessTrue() {
        assertTrue(Passed.Succeeded("S", "S").success)
    }

    @Test fun pendingHasSuccessTrue() {
        assertTrue(Passed.Pending("P", "P").success)
    }

    @Test fun excludedHasSuccessTrue() {
        assertTrue(Passed.Excluded("F", "F").success)
    }

    @Test fun informationHasSuccessTrue() {
        assertTrue(Passed.Information("I", "I").success)
    }

    // -------------------------------------------------------------------------
    // success flag: Failed subtypes (hoisted onto Failed itself, see Failed.success)
    // -------------------------------------------------------------------------

    @Test fun restrictedHasSuccessFalse() {
        assertFalse(Failed.Restricted("R", "R").success)
    }

    @Test fun invalidHasSuccessFalse() {
        assertFalse(Failed.Invalid("I", "I").success)
    }

    @Test fun rejectedHasSuccessFalse() {
        assertFalse(Failed.Rejected("E", "E").success)
    }

    @Test fun unservedHasSuccessFalse() {
        assertFalse(Failed.Unserved("U", "U").success)
    }

    // -------------------------------------------------------------------------
    // origin: defaults to "custom" for direct construction, overridable at the call site
    // -------------------------------------------------------------------------

    @Test fun originDefaultsToCustom() {
        assertEquals(StatusConstants.CUSTOM, Passed.Succeeded("S", "S").origin)
        assertEquals(StatusConstants.CUSTOM, Failed.Restricted("R", "R").origin)
    }

    @Test fun originIsOverridable() {
        assertEquals(StatusConstants.KIIT, Passed.Succeeded("S", "S", origin = StatusConstants.KIIT).origin)
    }

    // -------------------------------------------------------------------------
    // group: the discriminant field, exhaustive over all 8 subtypes
    // -------------------------------------------------------------------------

    @Test
    fun groupReturnsCorrectStringForAllSubtypes() {
        assertEquals("Succeeded", Passed.Succeeded("S", "S").group)
        assertEquals("Pending", Passed.Pending("P", "P").group)
        assertEquals("Excluded", Passed.Excluded("F", "F").group)
        assertEquals("Information", Passed.Information("N", "N").group)
        assertEquals("Restricted", Failed.Restricted("R", "R").group)
        assertEquals("Invalid", Failed.Invalid("I", "I").group)
        assertEquals("Rejected", Failed.Rejected("E", "E").group)
        assertEquals("Unserved", Failed.Unserved("U", "U").group)
    }

    // -------------------------------------------------------------------------
    // groupDescription: runtime-accessible version of each group's meaning
    // -------------------------------------------------------------------------

    @Test
    fun groupDescriptionIsNonBlankAndDistinctForAllSubtypes() {
        val descriptions =
            listOf(
                Passed.Succeeded("S", "S").groupDescription,
                Passed.Pending("P", "P").groupDescription,
                Passed.Excluded("F", "F").groupDescription,
                Passed.Information("N", "N").groupDescription,
                Failed.Restricted("R", "R").groupDescription,
                Failed.Invalid("I", "I").groupDescription,
                Failed.Rejected("E", "E").groupDescription,
                Failed.Unserved("U", "U").groupDescription,
            )
        assertTrue(descriptions.all { it.isNotBlank() })
        assertEquals(descriptions.size, descriptions.toSet().size)
    }

    @Test
    fun groupDescriptionIsConsistentAcrossInstancesOfTheSameSubtype() {
        assertEquals(Failed.Restricted("A", "A").groupDescription, Failed.Restricted("B", "B").groupDescription)
    }

    // -------------------------------------------------------------------------
    // scope: defaulted field on every concrete Passed/Failed subtype, "" means unset
    // -------------------------------------------------------------------------

    @Test
    fun scopeDefaultsToEmptyString() {
        assertEquals("", Failed.Restricted("RESTRICTED", "Restricted").scope)
        assertEquals("", Succeeded.SUCCESS.scope)
    }

    @Test
    fun scopeIsSettableViaConstructor() {
        val s = Failed.Restricted("RESTRICTED", "Restricted", origin = StatusConstants.KIIT, scope = "payments.cards")
        assertEquals("payments.cards", s.scope)
    }

    @Test
    fun scopeIsSettableViaCopyOnAnExistingInstanceIncludingBuiltIns() {
        val scoped = Succeeded.CREATED.copy(scope = "payments.cards")
        assertEquals("payments.cards", scoped.scope)
        assertEquals(Succeeded.CREATED.name, scoped.name)
        assertEquals(Succeeded.CREATED.origin, scoped.origin)
    }

    // -------------------------------------------------------------------------
    // statusKey: module-internal StatusKey(origin, scope, group, name), used to key
    // CodesToHttp/CodesToGrpc overrides.
    // -------------------------------------------------------------------------

    @Test
    fun statusKeyHasEmptyScopeByDefault() {
        val s = Failed.Restricted("RESTRICTED", "Restricted", origin = StatusConstants.KIIT)
        assertEquals(StatusKey(StatusConstants.KIIT, "", "Restricted", "RESTRICTED"), s.statusKey)
    }

    @Test
    fun statusKeyIncludesScopeWhenSet() {
        val s = Failed.Restricted("RESTRICTED", "Restricted", origin = StatusConstants.KIIT, scope = "payments.cards")
        assertEquals(StatusKey(StatusConstants.KIIT, "payments.cards", "Restricted", "RESTRICTED"), s.statusKey)
    }

    @Test
    fun statusKeyDiffersByScopeForOtherwiseIdenticalStatuses() {
        val base = Failed.Restricted("RESTRICTED", "Restricted", origin = StatusConstants.KIIT)
        val scopedA = base.copy(scope = "payments.cards")
        val scopedB = base.copy(scope = "payments.wallets")
        assertNotEquals(scopedA.statusKey, scopedB.statusKey)
        assertNotEquals(base.statusKey, scopedA.statusKey)
    }

    // -------------------------------------------------------------------------
    // path: "$origin:$scope" when scope is set, else just $origin
    // -------------------------------------------------------------------------

    @Test
    fun pathIsJustOriginWhenScopeIsUnset() {
        val s = Failed.Restricted("RESTRICTED", "Restricted", origin = StatusConstants.KIIT)
        assertEquals(StatusConstants.KIIT, s.path)
    }

    @Test
    fun pathIncludesScopeWhenSet() {
        val s = Failed.Restricted("RESTRICTED", "Restricted", origin = StatusConstants.KIIT, scope = "payments.cards")
        assertEquals("${StatusConstants.KIIT}:payments.cards", s.path)
    }

    // -------------------------------------------------------------------------
    // code: "${Passed|Failed}:$group:$name", stable across scope, not unique
    // -------------------------------------------------------------------------

    @Test
    fun codeForPassedStatus() {
        assertEquals("Passed:Succeeded:SUCCESS", Succeeded.SUCCESS.code)
    }

    @Test
    fun codeForFailedStatus() {
        val s = Failed.Restricted("RESTRICTED", "Restricted", origin = StatusConstants.KIIT)
        assertEquals("Failed:Restricted:RESTRICTED", s.code)
    }

    @Test
    fun codeIsUnaffectedByScope() {
        val s = Succeeded.SUCCESS.copy(scope = "payments.cards")
        assertEquals("Passed:Succeeded:SUCCESS", s.code)
    }

    @Test
    fun codeIsNotUniqueAcrossDifferentOriginsOrScopes() {
        val kiitDenied = Failed.Restricted("DENIED", "Denied", origin = StatusConstants.KIIT)
        val customDenied = Failed.Restricted("DENIED", "Custom denied", origin = "com.acme")
        assertEquals(kiitDenied.code, customDenied.code)
        assertNotEquals(kiitDenied.statusKey, customDenied.statusKey)
    }

    // -------------------------------------------------------------------------
    // ofStatus: selects correct instance based on message / rawStatus nullability
    // -------------------------------------------------------------------------

    @Test
    fun ofStatusReturnStatusWhenBothNull() {
        val status = Succeeded.SUCCESS
        assertSame(status, Status.ofStatus(null, null, status))
    }

    @Test
    fun ofStatusReturnsRawStatusWhenMessageIsNull() {
        val raw = Succeeded.CREATED
        val result = Status.ofStatus(null, raw, Succeeded.SUCCESS)
        assertSame(raw, result)
    }

    @Test
    fun ofStatusReturnsStatusWithUpdatedMessageWhenRawIsNull() {
        val result = Status.ofStatus("Custom", null, Succeeded.SUCCESS)
        assertEquals("Custom", result.message)
        assertEquals(Succeeded.SUCCESS.origin, result.origin)
    }

    @Test
    fun ofStatusReturnsRawWithUpdatedMessageWhenBothProvided() {
        val raw = Succeeded.CREATED
        val result = Status.ofStatus("Custom", raw, Succeeded.SUCCESS)
        assertEquals("Custom", result.message)
        assertEquals(raw.origin, result.origin)
        assertNotSame(raw, result)
    }

    // -------------------------------------------------------------------------
    // Typealiases (Restricted, Invalid, ...) are fully transparent: the same type as their
    // Failed.X/Passed.X target, not a copy. A `when` mixing aliased and fully-qualified branches
    // must still be exhaustive to the compiler, this is as much a compile-time check as a
    // runtime one, it wouldn't build if the aliases introduced a distinct type.
    // -------------------------------------------------------------------------

    @Test
    fun aliasedAndFullyQualifiedBranchesAreExhaustiveInTheSameWhen() {
        val status: Status = Restricted.DENIED
        val label =
            when (status) {
                is Restricted -> "restricted"
                is Failed -> "failed"
                is Passed -> "passed"
            }
        assertEquals("restricted", label)
    }

    @Test
    fun aliasedConstantIsSameInstanceAsFullyQualifiedConstant() {
        assertSame(Failed.Restricted.DENIED, Restricted.DENIED)
    }
}
