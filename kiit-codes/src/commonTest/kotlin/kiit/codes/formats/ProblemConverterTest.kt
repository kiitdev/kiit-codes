package kiit.codes.formats

import kiit.codes.CodesToHttp
import kiit.codes.Err
import kiit.codes.Failed
import kiit.codes.Invalid
import kiit.codes.Passed
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
            "https://stripe.com/docs/codes/payments/cards/failed/restricted/payment-requires-3ds",
            converter.convert(status).type,
        )
    }

    @Test
    fun plainIdOriginWithNoEntryGivesARelativeType() {
        val status = Failed.Rejected("OUT_OF_STOCK", "Out of stock", origin = "myapp1")
        assertEquals("/docs/codes/failed/rejected/out-of-stock", converter.convert(status).type)
    }

    @Test
    fun originIsLowercasedInTheDerivedBaseUrl() {
        val status = Failed.Rejected("OUT_OF_STOCK", "Out of stock", origin = "Stripe.com")
        assertEquals("https://stripe.com/docs/codes/failed/rejected/out-of-stock", converter.convert(status).type)
    }

    @Test
    fun emptyScopeIsSkippedAndScopeIsTheFirstSegmentWhenSet() {
        val noScope = Failed.Rejected("OUT_OF_STOCK", "Out of stock", origin = "stripe.com")
        val scoped = noScope.copy(scope = "payments.cards")
        assertEquals("https://stripe.com/docs/codes/failed/rejected/out-of-stock", converter.convert(noScope).type)
        assertEquals(
            "https://stripe.com/docs/codes/payments/cards/failed/rejected/out-of-stock",
            converter.convert(scoped).type,
        )
    }

    @Test
    fun convertUsesRegisteredBaseUrlAndFullTypePath() {
        val problems = ProblemConverter(mapOf("stripe.com" to "https://stripe.com/errors"))
        val status = Failed.Restricted("PAYMENT_REQUIRES_3DS", "3DS required", origin = "stripe.com", scope = "payments.cards")
        assertEquals("https://stripe.com/errors/payments/cards/failed/restricted/payment-requires-3ds", problems.convert(status).type)
    }

    @Test
    fun registeredEntryWinsOverTheOriginDerivedBaseUrl() {
        val problems = ProblemConverter(mapOf("stripe.com" to "https://docs.stripe.com/errors"))
        val status = Failed.Rejected("DUPLICATE_CHARGE", "Already processed", origin = "stripe.com", scope = "payments.cards")
        assertEquals("https://docs.stripe.com/errors/payments/cards/failed/rejected/duplicate-charge", problems.convert(status).type)
    }

    @Test
    fun registeredKeysAreLowercasedAndTrailingSlashIsTrimmed() {
        val problems = ProblemConverter(mapOf("Stripe.com" to "https://docs.stripe.com/errors/"))
        val status = Failed.Rejected("DUPLICATE_CHARGE", "Already processed", origin = "stripe.com")
        assertEquals("https://docs.stripe.com/errors/failed/rejected/duplicate-charge", problems.convert(status).type)
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
        assertEquals("https://stripe.com/docs/codes/errors/duplicate_charge", problem.type)
    }

    @Test
    fun acceptsAnExplicitMappingWithNoBaseUrls() {
        val problems = ProblemConverter(mapping = CodesToHttp())
        assertEquals(403, problems.convert(Restricted.FORBIDDEN).status)
    }

    @Test
    fun convertWithUrlTrimsATrailingSlash() {
        val status = Failed.Restricted("PAYMENT_REQUIRES_3DS", "3DS required", origin = "stripe.com")
        val problem = converter.convertWithUrl(status, baseUrl = "https://example.com/probs/")
        assertEquals("https://example.com/probs/failed/restricted/payment-requires-3ds", problem.type)
    }

    @Test
    fun convertCustomWithUrlMapsErrorsAndUsesTheExplicitBaseUrl() {
        val status = Failed.Invalid("BAD_EMAIL", "Bad email", origin = "stripe.com")
        val err = Err.ErrorList(errors = listOf(Err.ErrorField("email", "x", "Invalid email")), message = "Validation failed")
        val problem = converter.convertCustomWithUrl(status, err, "https://example.com/probs") { ErrorDetail("mapped", it.message) }
        assertEquals("https://example.com/probs/failed/invalid/bad-email", problem.type)
        assertEquals(ErrorDetail("mapped", "Invalid email"), problem.errors?.single())
    }

    @Test
    fun dottedScopeBecomesSlashSeparatedSegments() {
        val status = Failed.Rejected("DUPLICATE_CHARGE", "Duplicate", origin = "stripe.com", scope = "payments.cards")
        assertEquals(
            "https://stripe.com/docs/codes/payments/cards/failed/rejected/duplicate-charge",
            converter.convert(status).type,
        )
    }

    @Test
    fun deepScopeKeepsTheLastThreeSegmentsAsStatusGroupName() {
        val status = Failed.Rejected("DUPLICATE_CHARGE", "Duplicate", origin = "stripe.com", scope = "a.b.c.d")
        assertEquals(
            "https://stripe.com/docs/codes/a/b/c/d/failed/rejected/duplicate-charge",
            converter.convert(status).type,
        )
    }

    @Test
    fun scopeSegmentsAreLowercasedAndUnderscoresBecomeDashes() {
        val status = Failed.Rejected("DUPLICATE_CHARGE", "Duplicate", origin = "stripe.com", scope = "Pay_ments.Cards")
        assertEquals(
            "https://stripe.com/docs/codes/pay-ments/cards/failed/rejected/duplicate-charge",
            converter.convert(status).type,
        )
    }

    @Test
    fun passedStatusUsesThePassedSegment() {
        val status = Passed.Succeeded("ORDER_CREATED", "Order created", origin = "stripe.com")
        assertEquals("https://stripe.com/docs/codes/passed/succeeded/order-created", converter.convert(status).type)
    }

    @Test
    fun domainOriginsWithHyphensAndManyLabelsAreAbsolute() {
        val status = Failed.Rejected("OUT_OF_STOCK", "Out of stock", origin = "api.my-shop.example.com")
        assertEquals(
            "https://api.my-shop.example.com/docs/codes/failed/rejected/out-of-stock",
            converter.convert(status).type,
        )
    }

    @Test
    fun originsThatAreNotDomainsGiveARelativeType() {
        listOf("localhost", "localhost:8080", "my_app", "-bad.com", "bad-.com", "stripe.com.", "a..b", "my app.com").forEach { origin ->
            val status = Failed.Rejected("OUT_OF_STOCK", "Out of stock", origin = origin)
            assertEquals("/docs/codes/failed/rejected/out-of-stock", converter.convert(status).type, origin)
        }
    }

    @Test
    fun customOriginDefaultGivesARelativeType() {
        val status = Failed.Rejected("OUT_OF_STOCK", "Out of stock")
        assertEquals("/docs/codes/failed/rejected/out-of-stock", converter.convert(status).type)
    }

    @Test
    fun registeredEntryForANonDomainOriginIsAbsoluteWithTheNewSuffix() {
        val problems = ProblemConverter(mapOf("myapp1" to "https://docs.example.com/myapp1/errors"))
        val status = Failed.Rejected("OUT_OF_STOCK", "Out of stock", origin = "myapp1", scope = "shop.cart")
        assertEquals("https://docs.example.com/myapp1/errors/shop/cart/failed/rejected/out-of-stock", problems.convert(status).type)
    }

    @Test
    fun customTypeBuilderWithARelativeBase() {
        val status = Failed.Rejected("OUT_OF_STOCK", "Out of stock", origin = "myapp1")
        assertEquals("/docs/codes/stock/out_of_stock", converter.convert(status, typeBuilder = { "stock/${it.name.lowercase()}" }).type)
    }

    @Test
    fun emptyTypeBuilderSuffixLeavesJustTheBaseUrl() {
        val status = Failed.Rejected("OUT_OF_STOCK", "Out of stock", origin = "stripe.com")
        val problem = converter.convertWithUrl(status, baseUrl = "https://example.com/probs/out-of-credit", typeBuilder = { "" })
        assertEquals("https://example.com/probs/out-of-credit", problem.type)
    }

    @Test
    fun typeCanBeReplacedWithCopyForAnyUrl() {
        val problem = converter.convert(Restricted.FORBIDDEN).copy(type = "https://other.example.org/probs/forbidden")
        assertEquals("https://other.example.org/probs/forbidden", problem.type)
        assertEquals(403, problem.status)
    }

    @Test
    fun codeIsTheExactOriginScopeAndStatusCode() {
        val status = Failed.Rejected("DUPLICATE_CHARGE", "Duplicate", origin = "stripe.com", scope = "payments.cards")
        assertEquals("stripe.com:payments.cards:Failed:Rejected:DUPLICATE_CHARGE", converter.convert(status).code)
    }

    @Test
    fun codeKeepsAnEmptyScopeSlot() {
        val status = Failed.Rejected("OUT_OF_STOCK", "Out of stock", origin = "myapp1")
        assertEquals("myapp1::Failed:Rejected:OUT_OF_STOCK", converter.convert(status).code)
    }

    @Test
    fun codeForABuiltInStatus() {
        assertEquals("kiit.dev:codes:Failed:Restricted:FORBIDDEN", converter.convert(Restricted.FORBIDDEN).code)
    }

    @Test
    fun codeIsSetWithAnErrorListAndWithACustomTypeUrl() {
        val err = Err.ErrorList(errors = listOf(Err.ErrorField("email", "x", "Invalid email")), message = "Validation failed")
        val status = Failed.Invalid("BAD_EMAIL", "Bad email", origin = "stripe.com")
        assertEquals("stripe.com::Failed:Invalid:BAD_EMAIL", converter.convert(status, err).code)
        assertEquals("stripe.com::Failed:Invalid:BAD_EMAIL", converter.convertWithUrl(status, err, "https://example.com/probs").code)
    }

    @Test
    fun codeMatchesCodeDetailCodeForTheSameStatus() {
        val status = Failed.Rejected("DUPLICATE_CHARGE", "Duplicate", origin = "stripe.com", scope = "payments.cards")
        assertEquals(toCodeDetail(status).code, converter.convert(status).code)
        assertEquals(toCodeDetail(Restricted.FORBIDDEN).code, converter.convert(Restricted.FORBIDDEN).code)
    }

    @Test
    fun codeSurvivesReplacingTheType() {
        val problem = converter.convert(Restricted.FORBIDDEN).copy(type = "https://other.example.org/probs/forbidden")
        assertEquals("kiit.dev:codes:Failed:Restricted:FORBIDDEN", problem.code)
    }

    @Test
    fun titleIsStatusTitleAndStatusIsHttpCode() {
        val problem = converter.convert(Restricted.FORBIDDEN)
        assertEquals(Restricted.FORBIDDEN.title, problem.title)
        assertEquals(403, problem.status)
    }

    @Test
    fun singleErrorFieldKeepsItsFieldInErrors() {
        val problem = converter.convert(Invalid.INVALID_VALUE, Err.on("firstname", "Missing"))
        assertEquals("Missing", problem.detail)
        assertEquals(listOf(ErrorDetail("firstname", "Missing")), problem.errors)
    }

    @Test
    fun errorInfoGivesOneErrorEntryAndInstanceFromRef() {
        val problem = converter.convert(Invalid.INVALID_VALUE, Err.ErrorInfo("Balance too low", ref = "job-456"))
        assertEquals("Balance too low", problem.detail)
        assertEquals("job-456", problem.instance)
        assertEquals(listOf(ErrorDetail(null, "Balance too low")), problem.errors)
    }

    @Test
    fun nestedErrorListsAreFlattenedAndABlankListMessageFallsBackToTheFirstError() {
        val err =
            Err.ErrorList(
                errors =
                    listOf(
                        Err.ErrorField("a", "", "A is bad"),
                        Err.ErrorList(errors = listOf(Err.ErrorField("b", "", "B is bad")), message = "Inner"),
                    ),
                message = "",
            )
        val problem = converter.convert(Invalid.INVALID_VALUE, err)
        assertEquals("A is bad", problem.detail)
        assertEquals(listOf(ErrorDetail("a", "A is bad"), ErrorDetail("b", "B is bad")), problem.errors)
    }

    @Test
    fun emptyErrorListHasNoErrors() {
        val problem = converter.convert(Invalid.INVALID_VALUE, Err.ErrorList(errors = emptyList(), message = "Validation failed"))
        assertEquals("Validation failed", problem.detail)
        assertNull(problem.errors)
    }

    @Test
    fun customMapperSeesEveryLeafAndNeverAList() {
        val seen = mutableListOf<String>()
        val err =
            Err.ErrorList(
                errors = listOf(Err.ErrorField("a", "", "A"), Err.ErrorList(errors = listOf(Err.ErrorInfo("B")), message = "Inner")),
                message = "Outer",
            )
        converter.convertCustom(Invalid.INVALID_VALUE, err) {
            seen.add(it::class.simpleName.orEmpty())
            ErrorDetail(null, it.message)
        }
        assertEquals(listOf("ErrorField", "ErrorInfo"), seen)
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
