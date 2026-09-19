package kiit.codes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

// =================================================================================================
// DefaultStatusTest: Status.isDefault and each group's DEFAULT alias
// =================================================================================================

class DefaultStatusTest {
    private val defaults: List<Status> =
        listOf(
            Passed.Succeeded.DEFAULT,
            Passed.Pending.DEFAULT,
            Passed.Excluded.DEFAULT,
            Passed.Information.DEFAULT,
            Failed.Restricted.DEFAULT,
            Failed.Invalid.DEFAULT,
            Failed.Rejected.DEFAULT,
            Failed.Unserved.DEFAULT,
        )

    @Test
    fun defaultAliasesAreSameInstanceAsTheirBuiltIn() {
        assertSame(Passed.Succeeded.SUCCESS, Passed.Succeeded.DEFAULT)
        assertSame(Passed.Pending.ACCEPTED, Passed.Pending.DEFAULT)
        assertSame(Passed.Excluded.OMITTED, Passed.Excluded.DEFAULT)
        assertSame(Passed.Information.NOTICE, Passed.Information.DEFAULT)
        assertSame(Failed.Restricted.DENIED, Failed.Restricted.DEFAULT)
        assertSame(Failed.Invalid.INVALID_VALUE, Failed.Invalid.DEFAULT)
        assertSame(Failed.Rejected.RULE_VIOLATION, Failed.Rejected.DEFAULT)
        assertSame(Failed.Unserved.UNEXPECTED, Failed.Unserved.DEFAULT)
    }

    @Test
    fun everyGroupHasExactlyOneDefaultInRegistry() {
        val found = Codes.all.filter { it.isDefault }
        assertEquals(8, found.groupBy { it.group }.size)
        assertEquals(defaults.size, found.size)
        assertEquals(defaults.toSet(), found.toSet())
    }

    @Test
    fun defaultsReportIsDefault() {
        defaults.forEach { assertTrue(it.isDefault, it.name) }
    }

    // One non-default built-in per group, so every branch of Passed/Failed.isDefault sees a false.
    private val nonDefaults: List<Status> =
        listOf(
            Passed.Succeeded.CREATED,
            Passed.Pending.QUEUED,
            Passed.Excluded.SKIPPED,
            Passed.Information.ADVISORY,
            Failed.Restricted.UNAUTHENTICATED,
            Failed.Invalid.BAD_REQUEST,
            Failed.Rejected.CONFLICT,
            Failed.Unserved.UNSUPPORTED,
        )

    @Test
    fun nonDefaultBuiltInIsNotDefaultInEveryGroup() {
        assertEquals(8, nonDefaults.map { it.group }.toSet().size)
        nonDefaults.forEach { assertFalse(it.isDefault, it.name) }
    }

    @Test
    fun passedAndFailedDefaultsAreBothCovered() {
        assertTrue(defaults.filter { it.success }.all { it.isDefault })
        assertTrue(defaults.filterNot { it.success }.all { it.isDefault })
    }

    @Test
    fun nonDefaultsInOneGroupDoNotMatchAnotherGroupsDefault() {
        // Same name in a different group is a different status, only its own group's DEFAULT matches.
        assertFalse(Failed.Rejected("INVALID_VALUE", Failed.Invalid.DEFAULT.message, origin = StatusConstants.KIIT).isDefault)
        assertFalse(Passed.Pending("SUCCESS", Passed.Succeeded.DEFAULT.message, origin = StatusConstants.KIIT).isDefault)
    }

    @Test
    fun customStatusWithSameNameIsNotDefaultInEveryGroup() {
        val customs: List<Status> =
            defaults.map {
                when (it) {
                    is Passed.Succeeded -> Passed.Succeeded(it.name, it.message)
                    is Passed.Pending -> Passed.Pending(it.name, it.message)
                    is Passed.Excluded -> Passed.Excluded(it.name, it.message)
                    is Passed.Information -> Passed.Information(it.name, it.message)
                    is Failed.Restricted -> Failed.Restricted(it.name, it.message)
                    is Failed.Invalid -> Failed.Invalid(it.name, it.message)
                    is Failed.Rejected -> Failed.Rejected(it.name, it.message)
                    is Failed.Unserved -> Failed.Unserved(it.name, it.message)
                }
            }
        // Default origin is CUSTOM, not KIIT, so none of these equal a built-in default.
        customs.forEach { assertFalse(it.isDefault, it.name) }
    }

    @Test
    fun exactReplicaOfDefaultEqualsAndReportsDefault() {
        val d = Failed.Invalid.DEFAULT
        val replica = Failed.Invalid(d.name, d.message, origin = d.origin, scope = d.scope)
        assertEquals(d, replica)
        assertTrue(replica.isDefault)
    }

    @Test
    fun copyChangingAnyFieldIsNotDefaultInEveryGroup() {
        val copies: List<Status> =
            defaults.flatMap { listOf(it.withMessage(), it.withScope(), it.withOrigin(), it.withName()) }
        assertEquals(defaults.size * 4, copies.size)
        copies.forEach { assertFalse(it.isDefault, it.code) }
    }

    private fun Status.withMessage(): Status =
        when (this) {
            is Passed.Succeeded -> copy(message = "m")
            is Passed.Pending -> copy(message = "m")
            is Passed.Excluded -> copy(message = "m")
            is Passed.Information -> copy(message = "m")
            is Failed.Restricted -> copy(message = "m")
            is Failed.Invalid -> copy(message = "m")
            is Failed.Rejected -> copy(message = "m")
            is Failed.Unserved -> copy(message = "m")
        }

    private fun Status.withScope(): Status =
        when (this) {
            is Passed.Succeeded -> copy(scope = "s")
            is Passed.Pending -> copy(scope = "s")
            is Passed.Excluded -> copy(scope = "s")
            is Passed.Information -> copy(scope = "s")
            is Failed.Restricted -> copy(scope = "s")
            is Failed.Invalid -> copy(scope = "s")
            is Failed.Rejected -> copy(scope = "s")
            is Failed.Unserved -> copy(scope = "s")
        }

    private fun Status.withOrigin(): Status =
        when (this) {
            is Passed.Succeeded -> copy(origin = "o")
            is Passed.Pending -> copy(origin = "o")
            is Passed.Excluded -> copy(origin = "o")
            is Passed.Information -> copy(origin = "o")
            is Failed.Restricted -> copy(origin = "o")
            is Failed.Invalid -> copy(origin = "o")
            is Failed.Rejected -> copy(origin = "o")
            is Failed.Unserved -> copy(origin = "o")
        }

    private fun Status.withName(): Status =
        when (this) {
            is Passed.Succeeded -> copy(name = "n")
            is Passed.Pending -> copy(name = "n")
            is Passed.Excluded -> copy(name = "n")
            is Passed.Information -> copy(name = "n")
            is Failed.Restricted -> copy(name = "n")
            is Failed.Invalid -> copy(name = "n")
            is Failed.Rejected -> copy(name = "n")
            is Failed.Unserved -> copy(name = "n")
        }

    @Test
    fun copyWithUnchangedFieldsIsStillDefault() {
        assertTrue(Failed.Invalid.DEFAULT.copy().isDefault)
        assertTrue(Passed.Succeeded.DEFAULT.copy().isDefault)
    }
}
