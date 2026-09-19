package kiit.codes.formats

import kiit.codes.CodesToHttp
import kiit.codes.Err
import kiit.codes.Failed
import kiit.codes.Invalid
import kiit.codes.Restricted
import kiit.codes.StatusConstants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ProblemConverterTest {
    private val converter = ProblemConverter()

    @Test
    fun baseUrlDefaultsForKiitOriginWithCodeAsQueryParam() {
        val status = Failed.Restricted("PAYMENT_REQUIRES_3DS", "3DS required", origin = StatusConstants.KIIT)
        val problem = converter.convert(status)
        assertEquals(
            "https://www.kiit.dev/docs/kiit-codes?code=Failed:Restricted:PAYMENT_REQUIRES_3DS#taxonomy",
            problem.type,
        )
    }

    @Test
    fun domainOriginWithNoEntryBuildsTypeFromOrigin() {
        val status = Failed.Restricted("PAYMENT_REQUIRES_3DS", "3DS required", origin = "stripe.com", scope = "payments.cards")
        assertEquals(
            "https://stripe.com/problems/payments.cards/restricted/payment-requires-3ds",
            converter.convert(status).type,
        )
    }

    @Test
    fun plainIdOriginWithNoEntryDoesNotThrowAndIsUsedAsIs() {
        val status = Failed.Rejected("OUT_OF_STOCK", "Out of stock", origin = "myapp1")
        assertEquals("https://myapp1/problems/rejected/out-of-stock", converter.convert(status).type)
    }

    @Test
    fun originIsLowercasedInTheDerivedBaseUrl() {
        val status = Failed.Rejected("OUT_OF_STOCK", "Out of stock", origin = "MyApp1")
        assertEquals("https://myapp1/problems/rejected/out-of-stock", converter.convert(status).type)
    }

    @Test
    fun emptyScopeIsSkippedAndScopeIsTheFirstSegmentWhenSet() {
        val noScope = Failed.Rejected("OUT_OF_STOCK", "Out of stock", origin = "stripe.com")
        val scoped = noScope.copy(scope = "payments.cards")
        assertEquals("https://stripe.com/problems/rejected/out-of-stock", converter.convert(noScope).type)
        assertEquals("https://stripe.com/problems/payments.cards/rejected/out-of-stock", converter.convert(scoped).type)
    }

    @Test
    fun convertUsesRegisteredBaseUrlAndFullTypePath() {
        val problems = ProblemConverter(mapOf("com.stripe" to "https://stripe.com/problems"))
        val status = Failed.Restricted("PAYMENT_REQUIRES_3DS", "3DS required", origin = "com.stripe", scope = "payments.cards")
        assertEquals("https://stripe.com/problems/payments.cards/restricted/payment-requires-3ds", problems.convert(status).type)
    }

    @Test
    fun registeredEntryWinsOverTheOriginDerivedBaseUrl() {
        val problems = ProblemConverter(mapOf("stripe.com" to "https://docs.stripe.com/errors"))
        val status = Failed.Rejected("DUPLICATE_CHARGE", "Already processed", origin = "stripe.com", scope = "payments.cards")
        assertEquals("https://docs.stripe.com/errors/payments.cards/rejected/duplicate-charge", problems.convert(status).type)
    }

    @Test
    fun registeredKeysAreLowercasedAndTrailingSlashIsTrimmed() {
        val problems = ProblemConverter(mapOf("Stripe.com" to "https://docs.stripe.com/errors/"))
        val status = Failed.Rejected("DUPLICATE_CHARGE", "Already processed", origin = "stripe.com")
        assertEquals("https://docs.stripe.com/errors/rejected/duplicate-charge", problems.convert(status).type)
    }

    @Test
    fun kiitOriginIsFixedAndCannotBeOverridden() {
        val problems = ProblemConverter(mapOf(StatusConstants.KIIT to "https://example.com/problems"))
        assertEquals(
            "https://www.kiit.dev/docs/kiit-codes?code=Failed:Restricted:DENIED#taxonomy",
            problems.convert(Restricted.DENIED).type,
        )
    }

    @Test
    fun customTypeBuilderSuffixIsAppendedToTheOriginDerivedBaseUrl() {
        val status = Failed.Rejected("DUPLICATE_CHARGE", "Already processed", origin = "stripe.com")
        val problem = converter.convert(status, typeBuilder = { "errors/${it.name.lowercase()}" })
        assertEquals("https://stripe.com/problems/errors/duplicate_charge", problem.type)
    }

    @Test
    fun acceptsAnExplicitMappingWithNoBaseUrls() {
        val problems = ProblemConverter(mapping = CodesToHttp())
        assertEquals(403, problems.convert(Restricted.FORBIDDEN).status)
    }

    @Test
    fun convertWithUrlTrimsATrailingSlash() {
        val status = Failed.Restricted("PAYMENT_REQUIRES_3DS", "3DS required", origin = "com.stripe")
        val problem = converter.convertWithUrl(status, baseUrl = "https://example.com/probs/")
        assertEquals("https://example.com/probs/restricted/payment-requires-3ds", problem.type)
    }

    @Test
    fun convertCustomWithUrlMapsErrorsAndUsesTheExplicitBaseUrl() {
        val status = Failed.Invalid("BAD_EMAIL", "Bad email", origin = "stripe.com")
        val err = Err.ErrorList(errors = listOf(Err.ErrorField("email", "x", "Invalid email")), message = "Validation failed")
        val problem = converter.convertCustomWithUrl(status, err, "https://example.com/probs") { ErrorDetail("mapped", it.message) }
        assertEquals("https://example.com/probs/invalid/bad-email", problem.type)
        assertEquals(ErrorDetail("mapped", "Invalid email"), problem.errors?.single())
    }

    @Test
    fun titleIsStatusMessageAndStatusIsHttpCode() {
        val problem = converter.convert(Restricted.FORBIDDEN)
        assertEquals(Restricted.FORBIDDEN.message, problem.title)
        assertEquals(403, problem.status)
    }

    @Test
    fun noErrLeavesDetailAndInstanceAndErrorsNull() {
        val problem = converter.convert(Restricted.DENIED)
        assertNull(problem.detail)
        assertNull(problem.instance)
        assertNull(problem.errors)
    }

    @Test
    fun errorListPopulatesDefaultErrorDetailEntries() {
        val err =
            Err.ErrorList(
                errors = listOf(Err.ErrorField("email", "not-an-email", "Invalid email")),
                message = "Validation failed",
            )
        val problem = converter.convert(Invalid.INVALID_VALUE, err)
        assertEquals("Validation failed", problem.detail)
        assertEquals(ErrorDetail("email", "Invalid email"), problem.errors?.single())
    }

    @Test
    fun genericConvertMapsThroughACustomErrorItem() {
        data class RichError(override val field: String?, override val message: String, val ref: Any?) : ErrorItem

        val err =
            Err.ErrorList(
                errors = listOf(Err.ErrorField("email", "not-an-email", "Invalid email", ref = "req-42")),
                message = "Validation failed",
            )
        val problem =
            converter.convertCustom(Invalid.INVALID_VALUE, err) { RichError((it as? Err.ErrorField)?.field, it.message, it.ref) }
        assertEquals("req-42", (problem.errors?.single() as RichError).ref)
    }
}
