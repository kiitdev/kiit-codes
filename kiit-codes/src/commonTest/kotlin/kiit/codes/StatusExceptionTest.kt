package kiit.codes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.fail

// =================================================================================================
// StatusExceptionTest
// =================================================================================================

// Subclasses declared the way an outside caller would: extra data, custom message, narrowed access.

private class AccountLockedException(
    val accountId: String,
    val retryAfterSeconds: Int,
) : StatusException.RestrictedException(
        Restricted.DENIED,
        errors = listOf(Err.of("account $accountId is locked")),
    ) {
    override val message: String get() = "Account $accountId locked, retry in ${retryAfterSeconds}s"
}

private class QuotaExceededException(val limit: Int) :
    StatusException.UnservedException(Unserved.TIMEOUT, message = "Quota of $limit exceeded")

class StatusExceptionTest {
    @Test
    fun restrictedExceptionExposesItsStatus() {
        val ex = StatusException.RestrictedException(Restricted.UNAUTHORIZED)
        assertSame(Restricted.UNAUTHORIZED, ex.status)
    }

    @Test
    fun invalidExceptionExposesItsStatus() {
        val ex = StatusException.InvalidException(Invalid.BAD_REQUEST)
        assertSame(Invalid.BAD_REQUEST, ex.status)
    }

    @Test
    fun rejectedExceptionExposesItsStatus() {
        val ex = StatusException.RejectedException(Rejected.CONFLICT)
        assertSame(Rejected.CONFLICT, ex.status)
    }

    @Test
    fun unservedExceptionExposesItsStatus() {
        val ex = StatusException.UnservedException(Unserved.TIMEOUT)
        assertSame(Unserved.TIMEOUT, ex.status)
    }

    @Test
    fun defaultErrorsWrapTheStatusSingly() {
        val ex = StatusException.RestrictedException(Restricted.UNAUTHORIZED)
        assertEquals(1, ex.errors.size)
        assertEquals(Restricted.UNAUTHORIZED.title, ex.errors.single().message)
    }

    @Test
    fun explicitErrorsAreStoredInsteadOfTheDefault() {
        val errors = listOf(Err.of("bad field"))
        val ex = StatusException.InvalidException(Invalid.BAD_REQUEST, errors = errors)
        assertEquals(errors, ex.errors)
    }

    @Test
    fun causePropagatesToThrowableCause() {
        val root = IllegalStateException("root")
        val ex = StatusException.RejectedException(Rejected.CONFLICT, cause = root)
        assertSame(root, ex.cause)
    }

    @Test
    fun messageComesFromCheckedStatus() {
        val ex = StatusException.UnservedException(Unserved.UNREACHABLE)
        assertEquals(ex.checked.status.title, ex.message)
    }

    @Test
    fun messageDefaultsToTheStatusTitle() {
        val ex = StatusException.RejectedException(Rejected.CONFLICT)
        assertEquals(Rejected.CONFLICT.title, ex.message)
    }

    @Test
    fun explicitMessageReplacesTheTitleButNotTheDefaultError() {
        val ex = StatusException.RejectedException(Rejected.CONFLICT, message = "Order 5 already shipped")
        assertEquals("Order 5 already shipped", ex.message)
        // errors are independent of message: the default is still the status title
        assertEquals(Rejected.CONFLICT.title, ex.errors.single().message)
    }

    @Test
    fun messageAndErrorsAreIndependent() {
        val errors = listOf(Err.of("bad field"), Err.of("bad value"))
        val ex = StatusException.InvalidException(Invalid.BAD_REQUEST, "Validation failed", errors)
        assertEquals("Validation failed", ex.message)
        assertEquals(errors, ex.errors)
    }

    @Test
    fun statusIsNarrowedToTheGroupWithoutACast() {
        val restricted: Failed.Restricted = StatusException.RestrictedException(Restricted.UNAUTHORIZED).status
        val invalid: Failed.Invalid = StatusException.InvalidException(Invalid.BAD_REQUEST).status
        val rejected: Failed.Rejected = StatusException.RejectedException(Rejected.CONFLICT).status
        val unserved: Failed.Unserved = StatusException.UnservedException(Unserved.TIMEOUT).status
        assertSame(Restricted.UNAUTHORIZED, restricted)
        assertSame(Invalid.BAD_REQUEST, invalid)
        assertSame(Rejected.CONFLICT, rejected)
        assertSame(Unserved.TIMEOUT, unserved)
    }

    @Test
    fun statusAndCheckedStatusAreTheSameObject() {
        val ex = StatusException.UnservedException(Unserved.TIMEOUT)
        assertSame(ex.status, ex.checked.status)
    }

    @Test
    fun catchingAsSealedBaseNarrowsExhaustively() {
        val caught: StatusException =
            try {
                throw StatusException.InvalidException(Invalid.BAD_REQUEST)
            } catch (e: StatusException) {
                e
            }
        // Exhaustive `when` with no `else`, fails to compile if a subtype is missing.
        val label =
            when (caught) {
                is StatusException.RestrictedException -> "restricted"
                is StatusException.InvalidException -> "invalid"
                is StatusException.RejectedException -> "rejected"
                is StatusException.UnservedException -> "unserved"
            }
        assertEquals("invalid", label)
    }

    @Test
    fun narrowCatchDoesNotCatchMismatchedSubtype() {
        assertFailsWith<StatusException.InvalidException> {
            try {
                throw StatusException.InvalidException(Invalid.BAD_REQUEST)
            } catch (e: StatusException.RestrictedException) {
                fail("RestrictedException catch block should not run for an InvalidException")
            }
        }
    }

    // -------------------------------------------------------------------------
    // Failed.toException
    // -------------------------------------------------------------------------

    @Test
    fun toExceptionOnRestrictedProducesRestrictedException() {
        val ex = Restricted.UNAUTHORIZED.toException()
        assertEquals(StatusException.RestrictedException::class, ex::class)
    }

    @Test
    fun toExceptionOnInvalidProducesInvalidException() {
        val ex = Invalid.BAD_REQUEST.toException()
        assertEquals(StatusException.InvalidException::class, ex::class)
    }

    @Test
    fun toExceptionOnRejectedProducesRejectedException() {
        val ex = Rejected.CONFLICT.toException()
        assertEquals(StatusException.RejectedException::class, ex::class)
    }

    @Test
    fun toExceptionOnUnservedProducesUnservedException() {
        val ex = Unserved.TIMEOUT.toException()
        assertEquals(StatusException.UnservedException::class, ex::class)
    }

    @Test
    fun toExceptionPropagatesMessage() {
        val ex = Invalid.BAD_REQUEST.toException(message = "Validation failed")
        assertEquals("Validation failed", ex.message)
    }

    @Test
    fun toExceptionPropagatesExplicitErrors() {
        val errors = listOf(Err.of("bad field"))
        val ex = Invalid.BAD_REQUEST.toException(errors = errors)
        assertEquals(errors, ex.errors)
    }

    // -------------------------------------------------------------------------
    // Subclassing
    // -------------------------------------------------------------------------

    @Test
    fun subclassCarriesExtraDataAndKeepsStatusAndErrors() {
        val ex = AccountLockedException("a-1", 30)
        assertEquals("a-1", ex.accountId)
        assertSame(Restricted.DENIED, ex.status)
        assertEquals("account a-1 is locked", ex.errors.single().message)
    }

    @Test
    fun subclassCanOverrideMessage() {
        assertEquals("Account a-1 locked, retry in 30s", AccountLockedException("a-1", 30).message)
    }

    @Test
    fun subclassPassesMessageThroughSuper() {
        val ex = QuotaExceededException(10)
        assertEquals("Quota of 10 exceeded", ex.message)
        assertEquals(10, ex.limit)
        assertSame(Unserved.TIMEOUT, ex.status)
    }

    @Test
    fun subclassIsCaughtByGroupAndByBase() {
        val viaGroup =
            try {
                throw AccountLockedException("a-1", 30)
            } catch (e: StatusException.RestrictedException) {
                e
            }
        assertIs<AccountLockedException>(viaGroup)

        val viaBase: StatusException =
            try {
                throw AccountLockedException("a-1", 30)
            } catch (e: StatusException) {
                e
            }
        assertIs<StatusException.RestrictedException>(viaBase)
    }
}
