package kiit.codes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

// =================================================================================================
// CodeDetailTest: toCodeDetail() kiit-native occurrence-detail converter
// =================================================================================================

class CodeDetailTest {
    // -------------------------------------------------------------------------
    // path / code / message: read straight off Status, no baseUrl or HTTP status involved
    // -------------------------------------------------------------------------

    @Test
    fun pathCodeAndMessageComeStraightFromStatus() {
        val status = Restricted.DENIED
        val detail = toCodeDetail(status)
        assertEquals(status.path, detail.path)
        assertEquals(status.code, detail.code)
        assertEquals(status.message, detail.message)
    }

    @Test
    fun pathIncludesScopeAndCodeIsUnaffectedByIt() {
        val status = Failed.Rejected("DUPLICATE_CHARGE", "Already processed", origin = "com.stripe", scope = "payments.cards")
        val detail = toCodeDetail(status)
        assertEquals("com.stripe:payments.cards", detail.path)
        assertEquals("Failed:Rejected:DUPLICATE_CHARGE", detail.code)
    }

    @Test
    fun noHttpStatusOrTypeIsBuilt() {
        // Deliberately not a thing CodeDetail has, unlike ProblemDetail's type/status.
        val detail = toCodeDetail(Restricted.DENIED)
        assertEquals("dev.kiit", detail.path)
        assertEquals("Failed:Restricted:DENIED", detail.code)
    }

    // -------------------------------------------------------------------------
    // err: null -> detail/instance/errors stay null
    // -------------------------------------------------------------------------

    @Test
    fun noErrLeavesDetailAndInstanceAndErrorsNull() {
        val detail = toCodeDetail(Restricted.DENIED)
        assertNull(detail.detail)
        assertNull(detail.instance)
        assertNull(detail.errors)
    }

    // -------------------------------------------------------------------------
    // err: ErrorInfo/ErrorField -> detail/instance, errors stays null
    // -------------------------------------------------------------------------

    @Test
    fun errorInfoPopulatesDetailAndInstanceFromRef() {
        val err = Err.ErrorInfo("Balance too low", ref = "job-456")
        val detail = toCodeDetail(Restricted.DENIED, err)
        assertEquals("Balance too low", detail.detail)
        assertEquals("job-456", detail.instance)
        assertNull(detail.errors)
    }

    @Test
    fun errorInfoWithoutRefLeavesInstanceNull() {
        val err = Err.ErrorInfo("Balance too low")
        val detail = toCodeDetail(Restricted.DENIED, err)
        assertEquals("Balance too low", detail.detail)
        assertNull(detail.instance)
    }

    // -------------------------------------------------------------------------
    // err: ErrorList -> errors[], detail is the list's own message
    // -------------------------------------------------------------------------

    @Test
    fun errorListPopulatesErrorsAndDetail() {
        val err =
            Err.ErrorList(
                errors =
                    listOf(
                        Err.ErrorField("email", "not-an-email", "Invalid email"),
                        Err.ErrorInfo("Something else went wrong"),
                    ),
                message = "Validation failed",
            )
        val detail = toCodeDetail(Invalid.INVALID_VALUE, err)

        assertEquals("Validation failed", detail.detail)
        assertEquals(2, detail.errors?.size)
        assertEquals(ProblemError("email", "Invalid email"), detail.errors?.get(0))
        assertEquals(ProblemError(null, "Something else went wrong"), detail.errors?.get(1))
    }
}
