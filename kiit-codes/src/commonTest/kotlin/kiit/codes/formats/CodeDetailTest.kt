package kiit.codes.formats

import kiit.codes.Codes
import kiit.codes.CodesToHttp
import kiit.codes.Err
import kiit.codes.Failed
import kiit.codes.Invalid
import kiit.codes.Passed
import kiit.codes.Restricted
import kiit.codes.Status
import kiit.codes.StatusConstants
import kiit.codes.Succeeded
import kiit.codes.code
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun Status.withScope(scope: String): Status =
    when (this) {
        is Passed.Succeeded -> copy(scope = scope)
        is Passed.Pending -> copy(scope = scope)
        is Passed.Excluded -> copy(scope = scope)
        is Passed.Information -> copy(scope = scope)
        is Failed.Restricted -> copy(scope = scope)
        is Failed.Invalid -> copy(scope = scope)
        is Failed.Rejected -> copy(scope = scope)
        is Failed.Unserved -> copy(scope = scope)
    }

class CodeDetailTest {
    @Test
    fun codeSuccessAndMessageComeFromStatus() {
        val status = Restricted.DENIED
        val detail = toCodeDetail(status)
        assertEquals("${status.origin}:${status.scope}:${status.code}", detail.code)
        assertEquals(status.success, detail.success)
        assertEquals(status.title, detail.title)
    }

    @Test
    fun codeIncludesOriginAndScopeAndEndsWithStatusCode() {
        val status = Restricted.DENIED.copy(origin = "stripe.com", scope = "payments.cards")
        val detail = toCodeDetail(status)
        assertEquals("stripe.com:payments.cards:Failed:Restricted:DENIED", detail.code)
        assertTrue(detail.code.endsWith(status.code))
    }

    @Test
    fun builtInCodeHasTheCodesScope() {
        assertEquals("kiit.dev:codes:Failed:Restricted:DENIED", toCodeDetail(Restricted.DENIED).code)
    }

    @Test
    fun everyBuiltInHasTheCodesScopeAndFiveValues() {
        Codes.all.forEach {
            val code = toCodeDetail(it).code
            assertEquals(StatusConstants.CODES, it.scope, it.name)
            assertEquals("kiit.dev:codes:${it.code}", code, it.name)
            assertEquals(5, code.split(":").size, it.name)
        }
    }

    @Test
    fun everyBuiltInWithAnEmptyScopeKeepsItsSlot() {
        Codes.all.forEach {
            val code = toCodeDetail(it.withScope("")).code
            assertEquals("kiit.dev::${it.code}", code, it.name)
            assertEquals(5, code.split(":").size, it.name)
        }
    }

    @Test
    fun emptyScopeKeepsItsSlotSoCodeAlwaysHasFiveValues() {
        val code = toCodeDetail(Restricted.DENIED.copy(scope = "")).code
        assertEquals("kiit.dev::Failed:Restricted:DENIED", code)
        assertEquals(5, code.split(":").size)
    }

    @Test
    fun successIsTrueForPassedAndFalseForFailed() {
        assertTrue(toCodeDetail(Succeeded.SUCCESS).success)
        assertFalse(toCodeDetail(Restricted.DENIED).success)
    }

    @Test
    fun noErrLeavesDetailAndInstanceAndErrorsNull() {
        val detail = toCodeDetail(Restricted.DENIED)
        assertNull(detail.detail)
        assertNull(detail.instance)
        assertNull(detail.errors)
    }

    @Test
    fun statusIsNullWithoutAMapping() {
        assertNull(toCodeDetail(Restricted.DENIED).status)
    }

    @Test
    fun statusIsPopulatedWhenMappingIsSupplied() {
        val detail = toCodeDetail(Restricted.DENIED, mapping = CodesToHttp())
        assertEquals(401, detail.status)
    }

    @Test
    fun genericToCodeDetailAlsoPopulatesStatusWhenMappingIsSupplied() {
        data class RichError(override val field: String?, override val message: String) : ErrorItem

        val detail = toCodeDetail(Restricted.DENIED, null, CodesToHttp()) { RichError(null, it.message) }
        assertEquals(401, detail.status)
    }

    @Test
    fun errorInfoPopulatesDetailAndInstanceFromRef() {
        val err = Err.ErrorInfo("Balance too low", ref = "job-456")
        val detail = toCodeDetail(Restricted.DENIED, err)
        assertEquals("Balance too low", detail.detail)
        assertEquals("job-456", detail.instance)
    }

    @Test
    fun errorListPopulatesDefaultErrorDetailEntries() {
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
        assertEquals(listOf(ErrorDetail("email", "Invalid email"), ErrorDetail(null, "Something else went wrong")), detail.errors)
    }

    @Test
    fun genericToCodeDetailMapsThroughACustomErrorItem() {
        data class RichError(override val field: String?, override val message: String, val ref: Any?) : ErrorItem

        val err =
            Err.ErrorList(
                errors = listOf(Err.ErrorField("email", "not-an-email", "Invalid email", ref = "req-42")),
                message = "Validation failed",
            )
        val detail = toCodeDetail(Invalid.INVALID_VALUE, err) { RichError((it as? Err.ErrorField)?.field, it.message, it.ref) }
        assertEquals("req-42", (detail.errors?.single() as RichError).ref)
    }
}
