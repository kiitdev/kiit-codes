package kiit.codes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.fail

// =================================================================================================
// StatusExceptionTest
// =================================================================================================

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
        assertEquals(Restricted.UNAUTHORIZED.message, ex.errors.single().message)
    }

    @Test
    fun explicitErrorsAreStoredInsteadOfTheDefault() {
        val errors = listOf(Err.of("bad field"))
        val ex = StatusException.InvalidException(Invalid.BAD_REQUEST, errors)
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
        assertEquals(ex.checked.status.message, ex.message)
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
    fun toExceptionPropagatesExplicitErrors() {
        val errors = listOf(Err.of("bad field"))
        val ex = Invalid.BAD_REQUEST.toException(errors)
        assertEquals(errors, ex.errors)
    }
}
