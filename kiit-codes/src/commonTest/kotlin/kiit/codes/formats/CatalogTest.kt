package kiit.codes.formats

import kiit.codes.StatusConstants
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CatalogTest {
    @Test
    fun kiitOriginIsPreRegistered() {
        assertEquals("https://kiit.dev/problems", Catalog().baseUrlFor(StatusConstants.KIIT))
    }

    @Test
    fun unregisteredOriginReturnsNull() {
        assertNull(Catalog().baseUrlFor("com.stripe"))
    }

    @Test
    fun registerAddsAnOrigin() {
        val catalog = Catalog()
        catalog.register("com.stripe", "https://stripe.com/problems")
        assertEquals("https://stripe.com/problems", catalog.baseUrlFor("com.stripe"))
    }
}
