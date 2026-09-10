package kiit.codes.formats

class Catalog {
    private val baseUrls = mutableMapOf("dev.kiit" to "https://kiit.dev/problems")


    fun register(origin: String, baseUrl: String) {
        baseUrls[origin] = baseUrl
    }

    fun baseUrlFor(origin:String) : String? {
        return baseUrls[origin]
    }
}
