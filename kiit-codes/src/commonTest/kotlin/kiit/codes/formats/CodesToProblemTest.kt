package kiit.codes.formats

import kiit.codes.CodesToHttp
import kiit.codes.Err
import kiit.codes.Failed
import kiit.codes.Invalid
import kiit.codes.Restricted
import kiit.codes.StatusConstants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class CodesToProblemTest {
    private val catalog = Catalog()
    private val codesToProblem = CodesToProblem(catalog, CodesToHttp())

    @Test
    fun baseUrlDefaultsForKiitOrigin() {
        val status = Failed.Restricted("PAYMENT_REQUIRES_3DS", "3DS required", origin = StatusConstants.KIIT)
        val problem = codesToProblem.build(status)
        assertEquals("https://kiit.dev/problems/restricted/payment-requires-3ds", problem.type)
    }

    @Test
    fun buildThrowsForUnregisteredOrigin() {
        val status = Failed.Restricted("PAYMENT_REQUIRES_3DS", "3DS required", origin = "com.stripe")
        assertFailsWith<IllegalArgumentException> { codesToProblem.build(status) }
    }

    @Test
    fun buildUsesRegisteredBaseUrlAndFullTypePath() {
        catalog.register("com.stripe", "https://stripe.com/problems")
        val status = Failed.Restricted("PAYMENT_REQUIRES_3DS", "3DS required", origin = "com.stripe", scope = "payments.cards")
        val problem = codesToProblem.build(status)
        assertEquals("https://stripe.com/problems/payments.cards/restricted/payment-requires-3ds", problem.type)
    }

    @Test
    fun convertUsesExplicitBaseUrlRegardlessOfCatalog() {
        val problem = codesToProblem.convert(Restricted.DENIED, baseUrl = "https://example.com/probs")
        assertEquals("https://example.com/probs/restricted/denied", problem.type)
    }

    @Test
    fun titleIsStatusMessageAndStatusIsHttpCode() {
        val problem = codesToProblem.build(Restricted.FORBIDDEN)
        assertEquals(Restricted.FORBIDDEN.message, problem.title)
        assertEquals(403, problem.status)
    }

    @Test
    fun noErrLeavesDetailAndInstanceAndErrorsNull() {
        val problem = codesToProblem.build(Restricted.DENIED)
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
        val problem = codesToProblem.build(Invalid.INVALID_VALUE, err)
        assertEquals("Validation failed", problem.detail)
        assertEquals(ErrorDetail("email", "Invalid email"), problem.errors?.single())
    }

    @Test
    fun genericBuildMapsThroughACustomErrorItem() {
        data class RichError(override val field: String?, override val message: String, val ref: Any?) : ErrorItem

        val err =
            Err.ErrorList(
                errors = listOf(Err.ErrorField("email", "not-an-email", "Invalid email", ref = "req-42")),
                message = "Validation failed",
            )
        val problem =
            codesToProblem.buildCustom(Invalid.INVALID_VALUE, err) { RichError((it as? Err.ErrorField)?.field, it.message, it.ref) }
        assertEquals("req-42", (problem.errors?.single() as RichError).ref)
    }

    @Test
    fun problemForMatchesBuildWithDefaultErrorDetail() {
        val status = Restricted.FORBIDDEN
        assertEquals(codesToProblem.build(status), problemFor(catalog, CodesToHttp(), status))
    }
}
