package kiit.codes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

// =================================================================================================
// ProblemTest: toProblemDetail() RFC 9457 converter
// =================================================================================================

class ProblemTest {
    // -------------------------------------------------------------------------
    // baseUrl: defaulted only for kiit-codes' own origin, required otherwise
    // -------------------------------------------------------------------------

    @Test
    fun baseUrlDefaultsForKiitOrigin() {
        val status = Failed.Restricted("PAYMENT_REQUIRES_3DS", "3DS required", origin = StatusConstants.KIIT)
        val problem = toProblemDetail(status)
        assertEquals("https://kiit.dev/problems/dev.kiit/restricted/payment-requires-3ds", problem.type)
    }

    @Test
    fun baseUrlThrowsForNonKiitOriginWithoutExplicitBaseUrl() {
        val status = Failed.Restricted("PAYMENT_REQUIRES_3DS", "3DS required", origin = "com.stripe")
        assertFailsWith<IllegalArgumentException> { toProblemDetail(status) }
    }

    @Test
    fun explicitBaseUrlOverridesTheKiitDefault() {
        val status = Restricted.DENIED
        val problem = toProblemDetail(status, baseUrl = "https://example.com/probs")
        assertEquals("https://example.com/probs/dev.kiit/restricted/denied", problem.type)
    }

    @Test
    fun explicitBaseUrlSatisfiesNonKiitOrigin() {
        val status = Failed.Restricted("PAYMENT_REQUIRES_3DS", "3DS required", origin = "com.stripe")
        val problem = toProblemDetail(status, baseUrl = "https://stripe.com/problems")
        assertEquals("https://stripe.com/problems/com.stripe/restricted/payment-requires-3ds", problem.type)
    }

    // -------------------------------------------------------------------------
    // defaultTypeBuilder / type: origin/scope/group/name, lowercase-dash, scope only when present
    // -------------------------------------------------------------------------

    @Test
    fun typeOmitsScopeSegmentWhenScopeIsUnset() {
        val status = Failed.Restricted("PAYMENT_REQUIRES_3DS", "3DS required", origin = "com.stripe")
        val problem = toProblemDetail(status, baseUrl = "https://stripe.com/problems")
        assertEquals("https://stripe.com/problems/com.stripe/restricted/payment-requires-3ds", problem.type)
    }

    @Test
    fun typeIncludesScopeSegmentWhenSet() {
        val status =
            Failed.Restricted("PAYMENT_REQUIRES_3DS", "3DS required", origin = "com.stripe", scope = "payments.cards")
        val problem = toProblemDetail(status, baseUrl = "https://stripe.com/problems")
        assertEquals("https://stripe.com/problems/com.stripe/payments.cards/restricted/payment-requires-3ds", problem.type)
    }

    @Test
    fun typeDistinguishesCodesSharingOriginAndNameButDifferingGroup() {
        val created = Succeeded("CREATED", "Created", origin = "com.acme")
        val createdFailed = Failed.Invalid("CREATED", "Creation failed", origin = "com.acme")
        val a = toProblemDetail(created, baseUrl = "https://acme.example/problems")
        val b = toProblemDetail(createdFailed, baseUrl = "https://acme.example/problems")
        assertEquals("https://acme.example/problems/com.acme/succeeded/created", a.type)
        assertEquals("https://acme.example/problems/com.acme/invalid/created", b.type)
    }

    // -------------------------------------------------------------------------
    // typeBuilder: overridable, replaces the default construction entirely
    // -------------------------------------------------------------------------

    @Test
    fun typeBuilderOverridesDefaultConstruction() {
        val status = Failed.Restricted("PAYMENT_REQUIRES_3DS", "3DS required", origin = "com.stripe")
        val problem =
            toProblemDetail(
                status,
                baseUrl = "https://stripe.com/problems",
                typeBuilder = { "custom/${it.name.lowercase()}" },
            )
        assertEquals("https://stripe.com/problems/custom/payment_requires_3ds", problem.type)
    }

    // -------------------------------------------------------------------------
    // title / status: from Status.message / CodesToHttp.toCode
    // -------------------------------------------------------------------------

    @Test
    fun titleIsStatusMessageAndStatusIsHttpCode() {
        val problem = toProblemDetail(Restricted.FORBIDDEN)
        assertEquals(Restricted.FORBIDDEN.message, problem.title)
        assertEquals(403, problem.status)
    }

    // -------------------------------------------------------------------------
    // err: null -> detail/instance stay null
    // -------------------------------------------------------------------------

    @Test
    fun noErrLeavesDetailAndInstanceAndErrorsNull() {
        val problem = toProblemDetail(Restricted.DENIED)
        assertNull(problem.detail)
        assertNull(problem.instance)
        assertNull(problem.errors)
    }

    // -------------------------------------------------------------------------
    // err: ErrorInfo/ErrorField -> detail/instance, errors stays null
    // -------------------------------------------------------------------------

    @Test
    fun errorInfoPopulatesDetailAndInstanceFromRef() {
        val err = Err.ErrorInfo("Balance too low", ref = "req-123")
        val problem = toProblemDetail(Restricted.DENIED, err)
        assertEquals("Balance too low", problem.detail)
        assertEquals("req-123", problem.instance)
        assertNull(problem.errors)
    }

    @Test
    fun errorInfoWithoutRefLeavesInstanceNull() {
        val err = Err.ErrorInfo("Balance too low")
        val problem = toProblemDetail(Restricted.DENIED, err)
        assertEquals("Balance too low", problem.detail)
        assertNull(problem.instance)
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
        val problem = toProblemDetail(Invalid.INVALID_VALUE, err)

        assertEquals("Validation failed", problem.detail)
        assertEquals(2, problem.errors?.size)
        assertEquals(ProblemError("email", "Invalid email"), problem.errors?.get(0))
        assertEquals(ProblemError(null, "Something else went wrong"), problem.errors?.get(1))
    }

    // -------------------------------------------------------------------------
    // problemDetailFor: JS/TS-reachable proxy, same result as the default typeBuilder
    // -------------------------------------------------------------------------

    @Test
    fun problemDetailForMatchesToProblemDetailWithDefaultTypeBuilder() {
        val status = Restricted.FORBIDDEN
        assertEquals(toProblemDetail(status), problemDetailFor(status))
    }
}
