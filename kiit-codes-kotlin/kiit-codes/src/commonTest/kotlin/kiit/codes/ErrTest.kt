package kiit.codes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

// =================================================================================================
// ErrTest: the Err builder companion
// =================================================================================================

class ErrTest {
    @Test
    fun ofMessageBuildsErrorInfo() {
        val err = Err.of("bad thing")
        assertIs<Err.ErrorInfo>(err)
        assertEquals("bad thing", err.message)
        assertNull(err.cause)
    }

    @Test
    fun ofMessageWithThrowableCarriesCause() {
        val root = IllegalStateException("root")
        val err = Err.of("bad thing", root)
        assertSame(root, err.cause)
    }

    @Test
    fun ofStatusUsesStatusMessage() {
        val err = Err.of(Restricted.UNAUTHORIZED)
        assertIs<Err.ErrorInfo>(err)
        assertEquals(Restricted.UNAUTHORIZED.message, err.message)
    }

    @Test
    fun onFieldBuildsErrorField() {
        val err = Err.on("email", "not-an-email", "invalid email") as Err.ErrorField
        assertEquals("email", err.field)
        assertEquals("not-an-email", err.value)
        assertEquals("invalid email", err.message)
    }

    @Test
    fun onFieldWithoutValueDefaultsValueToEmpty() {
        val err = Err.on("password", "too short") as Err.ErrorField
        assertEquals("password", err.field)
        assertEquals("", err.value)
        assertEquals("too short", err.message)
    }

    @Test
    fun exBuildsErrorInfoFromThrowableMessage() {
        val root = IllegalStateException("boom")
        val err = Err.ex(root)
        assertEquals("boom", err.message)
        assertSame(root, err.cause)
    }

    @Test
    fun objBuildsErrorInfoWithRef() {
        val payload = mapOf("k" to "v")
        val err = Err.obj(payload)
        assertEquals(payload.toString(), err.message)
        assertSame(payload, err.ref)
    }

    @Test
    fun listBuildsErrorList() {
        val err = Err.list(listOf("one", "two"), "multiple errors")
        assertEquals("multiple errors", err.message)
        assertEquals(2, err.errors.size)
        assertTrue(err.errors.all { it is Err.ErrorInfo })
    }

    @Test
    fun listUsesDefaultMessageWhenNull() {
        val err = Err.list(listOf("one"), null)
        assertEquals("Error occurred", err.message)
    }

    @Test
    fun buildReturnsSameErrInstance() {
        val original = Err.of("already an err")
        assertSame(original, Err.build(original))
    }

    @Test
    fun buildWrapsStringAsErrorInfo() {
        val err = Err.build("plain string")
        assertIs<Err.ErrorInfo>(err)
        assertEquals("plain string", err.message)
    }

    @Test
    fun buildWrapsExceptionViaEx() {
        val root = RuntimeException("failure")
        val err = Err.build(root)
        assertEquals("failure", err.message)
        assertSame(root, err.cause)
    }

    @Test
    fun buildWrapsOtherObjectsViaObj() {
        val payload = 42
        val err = Err.build(payload)
        assertEquals("42", err.message)
        assertEquals(payload, err.ref)
    }

    @Test
    fun buildFallsBackToUnexpectedMessageForNull() {
        val err = Err.build(null)
        assertEquals(Unserved.UNEXPECTED.message, err.message)
    }
}
