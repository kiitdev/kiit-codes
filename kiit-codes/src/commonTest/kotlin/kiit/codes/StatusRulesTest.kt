package kiit.codes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StatusRulesTest {
    private val validOrigins =
        listOf("stripe.com", "Stripe.com", "myapp1", "api.my-shop.example.com", "my_app", "9lives", "o", "a".repeat(500))
    private val invalidOrigins =
        listOf(
            "", "host:8080", "a/b", "a b", "a?b", "a#b", "a%b", "a\\b", "a\"b", ".", "..", "-x", "_x", ".x",
            "é", "a\tb", "a\nb", "a b", "a b", " stripe.com",
        )
    private val validScopes = listOf("", "payments", "payments.cards", "Pay_ments.Cards", "a-b_c", "a..b", "9", "a".repeat(500))
    private val invalidScopes = listOf(" ", "a:b", "a/b", "a b", ".", "..", "-x", "_x", "é", "a\nb")
    private val validNames = listOf("PAYMENT_DECLINED", "out_of_stock", "A", "X9", "a-b", "a.b", "1ST")
    private val invalidNames = listOf("", "A:B", "A/B", "A B", "A?B", "A#B", "A%B", ".", "..", "-A", "_A", "É", "A\nB")

    @Test fun validValuesPass() {
        validOrigins.forEach { assertNull(StatusRules.check(it, "", "N"), "origin '$it'") }
        validScopes.forEach { assertNull(StatusRules.check("o", it, "N"), "scope '$it'") }
        validNames.forEach { assertNull(StatusRules.check("o", "", it), "name '$it'") }
    }

    @Test fun invalidValuesFail() {
        invalidOrigins.forEach { assertNotNull(StatusRules.check(it, "", "N"), "origin '$it'") }
        invalidScopes.forEach { assertNotNull(StatusRules.check("o", it, "N"), "scope '$it'") }
        invalidNames.forEach { assertNotNull(StatusRules.check("o", "", it), "name '$it'") }
    }

    @Test fun emptyScopeIsUnsetButEmptyOriginAndNameAreRejected() {
        assertNull(StatusRules.check("o", "", "N"))
        assertEquals("origin must not be empty", StatusRules.check("", "", "N"))
        assertEquals("name must not be empty", StatusRules.check("o", "", ""))
    }

    @Test fun messageNamesFieldAndValue() {
        assertEquals(
            "origin \"host:8080\" is invalid: allowed characters are a-z, A-Z, 0-9, '.', '-', '_'",
            StatusRules.check("host:8080", "", "N"),
        )
        assertEquals("name \"..\" is invalid: must start with a letter or digit", StatusRules.check("o", "", ".."))
    }

    @Test fun messageEscapesControlCharactersAndCutsLongValues() {
        val message = StatusRules.check("o", "", "A\nB")
        assertTrue(message!!.contains("A\\u000aB"))
        assertTrue(!message.contains('\n'))

        val long = StatusRules.check("o", "", "A".repeat(100) + ":")
        assertTrue(long!!.contains("\"" + "A".repeat(32) + "...\""))
    }

    @Test fun reportsOriginBeforeScopeBeforeName() {
        assertTrue(StatusRules.check("a:b", "c:d", "E:F")!!.startsWith("origin"))
        assertTrue(StatusRules.check("o", "c:d", "E:F")!!.startsWith("scope"))
    }

    // -------------------------------------------------------------------------
    // Constructors
    // -------------------------------------------------------------------------

    @Test fun everySubtypeThrowsOnBadValues() {
        val builders: List<(String, String, String) -> Status> =
            listOf(
                { o, s, n -> Passed.Succeeded(n, "t", o, s) },
                { o, s, n -> Passed.Pending(n, "t", o, s) },
                { o, s, n -> Passed.Excluded(n, "t", o, s) },
                { o, s, n -> Passed.Information(n, "t", o, s) },
                { o, s, n -> Failed.Restricted(n, "t", o, s) },
                { o, s, n -> Failed.Invalid(n, "t", o, s) },
                { o, s, n -> Failed.Rejected(n, "t", o, s) },
                { o, s, n -> Failed.Unserved(n, "t", o, s) },
            )
        for (build in builders) {
            build("stripe.com", "payments.cards", "N")
            assertFailsWith<IllegalArgumentException> { build("host:8080", "", "N") }
            assertFailsWith<IllegalArgumentException> { build("o", "a/b", "N") }
            assertFailsWith<IllegalArgumentException> { build("o", "", "A B") }
            assertFailsWith<IllegalArgumentException> { build("", "", "N") }
        }
    }

    @Test fun constructorMessageIsTheCheckMessage() {
        val e = assertFailsWith<IllegalArgumentException> { Failed.Rejected("A:B", "t") }
        assertEquals(StatusRules.check("custom", "", "A:B"), e.message)
    }

    @Test fun copyIsChecked() {
        assertFailsWith<IllegalArgumentException> { Failed.Rejected.CONFLICT.copy(scope = "a/b") }
        assertFailsWith<IllegalArgumentException> { Failed.Rejected.CONFLICT.copy(origin = "a:b") }
        assertEquals("payments.cards", Failed.Rejected.CONFLICT.copy(scope = "payments.cards").scope)
    }

    @Test fun everyBuiltInPasses() {
        Codes.all.forEach { assertNull(StatusRules.check(it.origin, it.scope, it.name), it.code) }
    }
}
