package kiit.codes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

// =================================================================================================
// CheckedTest
// =================================================================================================

class CheckedTest {
    fun validatePhone(phone: String): Checked {
        return if (phone.length >= 10) {
            Checked.success()
        } else {
            Checked.failure(
                Invalid.INVALID_VALUE,
                listOf(Err.on("phone", phone, "Too short")),
            )
        }
    }

    // -------------------------------------------------------------------------
    // validatePhone: a realistic Checked-returning validator, also exercised via collect below
    // -------------------------------------------------------------------------

    @Test
    fun validatePhoneSucceedsForLongEnoughNumber() {
        val checked = validatePhone("5551234567")
        assertTrue(checked.isValid)
        assertSame(Succeeded.SUCCESS, checked.status)
    }

    @Test
    fun validatePhoneFailsForTooShortNumber() {
        val checked = validatePhone("12345")
        assertTrue(!checked.isValid)
        assertEquals(Invalid.INVALID_VALUE, checked.status)
        assertEquals(1, checked.errors.size)
        assertEquals("Too short", checked.errors.single().message)
    }

    @Test
    fun successDefaultsToCodesSuccess() {
        val checked = Checked.success()
        assertSame(Succeeded.SUCCESS, checked.status)
        assertTrue(checked.errors.isEmpty())
    }

    @Test
    fun successAcceptsCustomPassedStatus() {
        val checked = Checked.success(Succeeded.CREATED)
        assertSame(Succeeded.CREATED, checked.status)
        assertTrue(checked.errors.isEmpty())
    }

    @Test
    fun failureCarriesStatusAndErrors() {
        val errors = listOf(Err.of("bad field"))
        val checked = Checked.failure(Invalid.BAD_REQUEST, errors)
        assertSame(Invalid.BAD_REQUEST, checked.status)
        assertEquals(errors, checked.errors)
    }

    @Test
    fun failureRequiresAtLeastOneError() {
        assertFailsWith<IllegalArgumentException> {
            Checked.failure(Invalid.BAD_REQUEST, emptyList())
        }
    }

    @Test
    fun isValidIsTrueForSuccessFalseForFailure() {
        assertTrue(Checked.success().isValid)
        assertTrue(!Checked.failure(Invalid.BAD_REQUEST, listOf(Err.of("bad field"))).isValid)
    }

    @Test
    fun implementsHasErrors() {
        val checked = Checked.failure(Invalid.BAD_REQUEST, listOf(Err.of("bad field")))
        val hasErrors: HasErrors = checked
        assertEquals(checked.errors, hasErrors.errors)
    }

    @Test
    fun implementsHasStatus() {
        val checked = Checked.failure(Invalid.BAD_REQUEST, listOf(Err.of("bad field")))
        val hasStatus: HasStatus<Status> = checked
        assertEquals(checked.status, hasStatus.status)
    }

    // -------------------------------------------------------------------------
    // collect
    // -------------------------------------------------------------------------

    @Test
    fun collectWithNoChecksSucceeds() {
        val result = collect()
        assertSame(Succeeded.SUCCESS, result.status)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun collectWithAllPassingChecksSucceeds() {
        val result = collect(Checked.success(), Checked.success(Succeeded.CREATED))
        assertSame(Succeeded.SUCCESS, result.status)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun collectWithOneFailurePoolsItsErrors() {
        val errors = listOf(Err.of("bad field"))
        val result = collect(Checked.success(), Checked.failure(Invalid.BAD_REQUEST, errors))
        assertEquals(Invalid.INVALID_VALUE, result.status)
        assertEquals(errors, result.errors)
    }

    @Test
    fun collectWithMultipleFailuresPoolsAllErrorsInOrder() {
        val firstErrors = listOf(Err.of("first"))
        val secondErrors = listOf(Err.of("second"), Err.of("third"))
        val result =
            collect(
                Checked.failure(Invalid.BAD_REQUEST, firstErrors),
                Checked.success(),
                Checked.failure(Invalid.NOT_FOUND, secondErrors),
            )
        assertEquals(Invalid.INVALID_VALUE, result.status)
        assertEquals(firstErrors + secondErrors, result.errors)
    }

    @Test
    fun collectSucceedsWhenValidatePhonePassesAlongsideOtherChecks() {
        val result = collect(Checked.success(), validatePhone("5551234567"))
        assertSame(Succeeded.SUCCESS, result.status)
        assertTrue(result.errors.isEmpty())
    }

    @Test
    fun collectPoolsValidatePhoneErrorsWithOtherFailures() {
        val emailErrors = listOf(Err.on("email", "must contain @"))
        val result = collect(Checked.failure(Invalid.INVALID_VALUE, emailErrors), validatePhone("123"))
        assertEquals(Invalid.INVALID_VALUE, result.status)
        assertEquals(emailErrors + validatePhone("123").errors, result.errors)
    }

    @Test
    fun collectOverListBehavesIdenticallyToVararg() {
        val firstErrors = listOf(Err.of("first"))
        val secondErrors = listOf(Err.of("second"), Err.of("third"))
        val checks =
            listOf(
                Checked.failure(Invalid.BAD_REQUEST, firstErrors),
                Checked.success(),
                Checked.failure(Invalid.NOT_FOUND, secondErrors),
            )
        val fromList = collect(checks)
        val fromVararg = collect(*checks.toTypedArray())
        assertEquals(fromVararg.status, fromList.status)
        assertEquals(fromVararg.errors, fromList.errors)
    }
}
