package kiit.codes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Kotlin/JS runtime coverage of [jsMain]'s `StatusError.kt` shims — complements
 * `samples/sample-ts`, which verifies the *generated `.d.ts` shape* from real TypeScript; this
 * verifies the compiled JS actually behaves correctly at runtime. Rides the already-CI-wired
 * `jsNodeTest` task, no new CI config needed.
 */
class StatusErrorTest {
    @Test
    fun restrictedErrorCarriesStatusAndDefaultsErrors() {
        val e =
            assertFailsWith<RestrictedError> {
                throw RestrictedError(Failed.Restricted.UNAUTHENTICATED)
            }
        assertEquals("UNAUTHENTICATED", e.restrictedStatus.name)
        assertEquals(1, e.exportedErrors.size)
    }

    @Test
    fun restrictedErrorExposesExplicitErrors() {
        val err = Err.of("token missing")
        val e = RestrictedError(Failed.Restricted.UNAUTHENTICATED, listOf(err))
        assertEquals(listOf(err), e.exportedErrors)
    }

    @Test
    fun invalidErrorCarriesStatus() {
        val e = InvalidError(Failed.Invalid.BAD_REQUEST)
        assertEquals("BAD_REQUEST", e.invalidStatus.name)
    }

    @Test
    fun rejectedErrorCarriesStatus() {
        val e = RejectedError(Failed.Rejected.CONFLICT)
        assertEquals("CONFLICT", e.rejectedStatus.name)
    }

    @Test
    fun unservedErrorCarriesStatus() {
        val e = UnservedError(Failed.Unserved.TIMEOUT)
        assertEquals("TIMEOUT", e.unservedStatus.name)
    }
}
