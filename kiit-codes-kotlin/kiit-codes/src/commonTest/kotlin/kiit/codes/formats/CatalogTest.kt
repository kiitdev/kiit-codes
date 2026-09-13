package kiit.codes.formats

import kiit.codes.StatusConstants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CatalogTest {
    @Test
    fun kiitOriginDefaultsWhenNotSupplied() {
        assertEquals("https://www.kiit.dev/docs/kiit-codes", Catalog.of().baseUrlFor(StatusConstants.KIIT))
    }

    @Test
    fun unregisteredOriginReturnsNull() {
        assertNull(Catalog.of().baseUrlFor("com.stripe"))
    }

    @Test
    fun ofAddsEachSuppliedOrigin() {
        val catalog = Catalog.of(mapOf("com.stripe" to "https://stripe.com/problems"))
        assertEquals("https://stripe.com/problems", catalog.baseUrlFor("com.stripe"))
    }

    @Test
    fun ofIgnoresASuppliedKiitOverride() {
        val catalog = Catalog.of(mapOf(StatusConstants.KIIT to "https://example.com/problems"))
        assertEquals("https://www.kiit.dev/docs/kiit-codes", catalog.baseUrlFor(StatusConstants.KIIT))
    }
}
