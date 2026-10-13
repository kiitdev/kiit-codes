/** url: www.kiit.dev */
package kiit.codes.formats

import kiit.codes.CodesToHttp
import kiit.codes.Err
import kiit.codes.Status
import kiit.codes.StatusConstants
import kiit.codes.code
import kotlin.jvm.JvmOverloads

private const val KIIT_BASE_URL = "https://www.kiit.dev/docs/kiit-codes"
private const val DOCS_PATH = "/docs/codes"

// Two or more dot-separated labels of letters, digits and hyphens, e.g. "stripe.com". Form only, no DNS lookup.
private val DOMAIN = Regex("^[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)+$")

private fun isDomain(origin: String): Boolean = DOMAIN.matches(origin)

/**
 * Converts a [Status] and an optional [Err] into an [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457.html)
 * [Problem]. Like [CodesToHttp] and [kiit.codes.CodesToGrpc], it turns a status into another representation.
 *
 *
 * TERMS
 * 1. origin: [Status.origin], the key of each [baseUrls] entry, e.g. `"stripe.com"`.
 * 2. baseUrl: text before the suffix, path prefix included, no trailing slash. e.g. `"https://stripe.com/errors"`
 * 3. suffix: `{scope}/{code}`, where `{code}` is `{status}/{group}/{name}`. Every segment is exact, with no case change
 *    or character replacement. Each `.` in the scope starts a new segment, an empty scope is skipped, and status is
 *    `Passed` or `Failed`. e.g. `"payments/cards/Failed/Rejected/DUPLICATE_CHARGE"`
 * 4. type: the RFC 9457 `type` field, `{baseUrl}/{suffix}`. It is the problem's identity as well as its docs pointer.
 *
 *
 * OPTIONAL
 * 1. [baseUrls] is optional.
 * 2. Without an entry, the baseUrl comes from the origin, lowercased. A domain origin (two or more dot-separated
 *    labels of letters, digits and hyphens) gives `https://{origin}/docs/codes`. Any other origin, such as
 *    `"myapp1"`, gives the relative `/docs/codes`. Only the form is checked, not DNS.
 * 3. The relative form is a fallback. RFC 9457 prefers absolute URIs, and a relative `type` resolves against the base
 *    URI of the response, so it points at the API's own host. Add an entry to get an absolute URL.
 * 4. An entry wins over the origin-derived baseUrl. It is always absolute, and only the suffix is built for it.
 * 5. `type` is the problem's identity, so pick the base once and keep it. Two statuses must not produce the same
 *    `type`. An origin, a scope and a name can only contain the characters [StatusRules] allows.
 *
 *
 * EXAMPLE
 * 1. origin `"stripe.com"` -> baseUrl `"https://stripe.com/errors"`
 * 2. origin `"myapp1"` -> baseUrl `"https://docs.example.com/myapp1/errors"`
 *
 * ```kotlin
 * val converter = ProblemConverter(baseUrls = mapOf("stripe.com" to "https://stripe.com/errors"))
 * val problem = converter.convert(status, err)
 * ```
 *
 * With the first entry, a `Rejected` status named `DUPLICATE_CHARGE` with scope `payments.cards` converts to:
 * ```json
 * {
 *     "type": "https://stripe.com/errors/payments/cards/Failed/Rejected/DUPLICATE_CHARGE",
 *     "title": "This charge has already been processed",
 *     "status": 409
 * }
 * ```
 *
 * With no entry, the same status with origin `"stripe.com"` gets the `type`
 * `https://stripe.com/docs/codes/payments/cards/Failed/Rejected/DUPLICATE_CHARGE`. A status with the origin `"myapp1"`
 * and no scope gets `/docs/codes/Failed/Rejected/OUT_OF_STOCK`.
 *
 *
 * NOTES
 * 1. Keys of [baseUrls] are lowercased and a trailing `/` on a value is trimmed.
 * 2. [StatusConstants.KIIT] is always present and resolves to kiit-codes' own taxonomy docs.
 * 3. [StatusConstants.KIIT] can't be overridden. Its suffix is `?code=...#codes`, joined with no `/`. The origin is
 *    reserved for kiit-authored codes. It is a convention, nothing checks it.
 * 4. Every `convert*` function ends in [convertCustomWithUrl], which is the only place a [Problem] is assembled.
 * 5. See [defaultTypeBuilder] for the suffix, and [Problem] for the RFC 9457 shape.
 * 6. [Problem.code] carries the full identity, origin and scope included. The suffix is a readable form of the same
 *    values and is not meant to be parsed.
 * 7. The suffix rules belong to [defaultTypeBuilder]. For another format pass a `typeBuilder`, use [convertWithUrl]
 *    with a base and an empty suffix, or replace the field with `copy(type = ...)` for any URL.
 *
 * @param baseUrls optional map of origin to baseUrl. Keys are lowercased, a trailing `/` on a value is trimmed.
 * @param mapping supplies the `status` code of each [Problem], defaults to [CodesToHttp]'s standard mapping.
 */
class ProblemConverter
    @JvmOverloads
    constructor(
        baseUrls: Map<String, String> = emptyMap(),
        private val mapping: CodesToHttp = CodesToHttp(),
    ) {
        private val registered: Map<String, String> =
            baseUrls.entries.associate { (origin, baseUrl) -> origin.lowercase() to baseUrl.trimEnd('/') } +
                (StatusConstants.KIIT to KIIT_BASE_URL)

        /**
         * Converts [status] and [err] into a [Problem]\<[ErrorDetail]\>. The baseUrl comes from [baseUrls], or from
         * the origin when there is no entry.
         *
         * @param status the status to convert.
         * @param err optional error. Every error in it goes into `errors`: an [Err.ErrorList] is expanded, recursively,
         *   and any other [Err] is one entry, so an [Err.ErrorField] keeps its field. `detail` is the error's message,
         *   or the first non-blank error message when that is blank. A single error also fills `instance` from `ref`.
         * @param typeBuilder returns the `type` suffix, not a full URL. Defaults to [defaultTypeBuilder].
         */
        @Suppress("ktlint:standard:function-signature")
        @JvmOverloads
        fun convert(
            status: Status,
            err: Err? = null,
            typeBuilder: (Status) -> String = ::defaultTypeBuilder,
        ): Problem<ErrorDetail> {
            return convertCustom(status, err, typeBuilder, ::defaultErrorItem)
        }

        /**
         * Same as [convert], but each error goes through [mapper] into a [Problem]\<[T]\>. Use this
         * for anything richer than field + message, see [ErrorItem].
         *
         * It is named differently from [convert], not an overload, because both end in a function parameter and
         * Kotlin can't tell a `convert(status, err) { ... }` call meant for [typeBuilder] from one meant for [mapper].
         * [mapper] comes last so it can be passed as a trailing lambda.
         *
         * @param status the status to convert.
         * @param err optional error, see [convert].
         * @param typeBuilder returns the `type` suffix, not a full URL. Defaults to [defaultTypeBuilder].
         * @param mapper turns each error into a [T].
         */
        fun <T : ErrorItem> convertCustom(
            status: Status,
            err: Err?,
            typeBuilder: (Status) -> String = ::defaultTypeBuilder,
            mapper: (Err) -> T,
        ): Problem<T> {
            return convertCustomWithUrl(status, err, baseUrlFor(status), typeBuilder, mapper)
        }

        /**
         * Same as [convert], but with an explicit [baseUrl]. [baseUrls] and the origin are not used.
         *
         * @param status the status to convert.
         * @param err optional error, see [convert].
         * @param baseUrl text before the suffix, path prefix included. A trailing `/` is trimmed.
         * @param typeBuilder returns the `type` suffix, not a full URL. Defaults to [defaultTypeBuilder].
         */
        @JvmOverloads
        fun convertWithUrl(
            status: Status,
            err: Err? = null,
            baseUrl: String,
            typeBuilder: (Status) -> String = ::defaultTypeBuilder,
        ): Problem<ErrorDetail> {
            return convertCustomWithUrl(status, err, baseUrl, typeBuilder, ::defaultErrorItem)
        }

        /**
         * Same as [convertWithUrl], but each [Err.ErrorList] entry goes through [mapper] into a [Problem]\<[T]\>.
         * This is the one function that assembles the [Problem], every other `convert*` function ends here.
         *
         * @param status the status to convert.
         * @param err optional error, see [convert].
         * @param baseUrl text before the suffix, path prefix included. A trailing `/` is trimmed.
         * @param typeBuilder returns the `type` suffix, not a full URL. Defaults to [defaultTypeBuilder].
         * @param mapper turns each error into a [T].
         */
        fun <T : ErrorItem> convertCustomWithUrl(
            status: Status,
            err: Err?,
            baseUrl: String,
            typeBuilder: (Status) -> String = ::defaultTypeBuilder,
            mapper: (Err) -> T,
        ): Problem<T> {
            val base = baseUrl.trimEnd('/')
            val path = typeBuilder(status)
            val type =
                when {
                    path.isEmpty() -> base
                    path.startsWith("?") || path.startsWith("#") -> "$base$path"
                    else -> "$base/$path"
                }
            val code = mapping.toCode(status)
            val parts = err.toParts(mapper)
            return Problem(
                type = type,
                title = status.title,
                status = code,
                detail = parts.detail,
                instance = parts.instance,
                errors = parts.errors,
                code = status.detailCode(),
            )
        }

        private fun baseUrlFor(status: Status): String {
            val origin = status.origin.lowercase()
            return registered[origin] ?: if (isDomain(origin)) "https://$origin$DOCS_PATH" else DOCS_PATH
        }
    }

/**
 * Default `type` suffix appended to the baseUrl: `scope`/`{code}`, where `{code}` is `status`/`group`/`name`. Every
 * segment is the exact value of the status, with no case change or character replacement, so the last three segments
 * are the same `Passed` or `Failed`, group and name as [Status.code], joined with `/` instead of `:`. Each `.` in the
 * `scope` starts a new segment and an empty `scope` is skipped. `origin` is left out on purpose, the baseUrl is
 * already specific to one origin (see [ProblemConverter]), so repeating it would just name that origin twice.
 *
 * An origin, a scope and a name can only contain the characters [StatusRules] allows, so no segment can contain `/`
 * or `:`.
 *
 * ```kotlin
 * val status = Failed.Rejected("DUPLICATE_CHARGE", "Duplicate", origin = "stripe.com", scope = "payments.cards")
 * defaultTypeBuilder(status)   // "payments/cards/Failed/Rejected/DUPLICATE_CHARGE"
 *
 * val noScope = Failed.Rejected("OUT_OF_STOCK", "Out of stock", origin = "myapp1")
 * defaultTypeBuilder(noScope)  // "Failed/Rejected/OUT_OF_STOCK"
 * ```
 *
 * This is the default suffix only. It applies with or without a `baseUrls` entry. Pass your own `typeBuilder` to
 * [ProblemConverter.convert] for another format.
 *
 * [StatusConstants.KIIT] is the exception: kiit-codes' docs have a Codes section but no page per code, so every
 * kiit-origin [Status] instead gets `status.code` as a query param on that page, e.g.
 * `?code=Failed:Invalid:INVALID_VALUE#codes`. A `code` is a colon-delimited identifier (letters, digits,
 * underscores), safe unencoded in a URI query, and it keeps each built-in's `type` different. The origin is reserved
 * for kiit-authored codes, a custom status must not use it.
 *
 * @param status the status to build the suffix for.
 * @return the suffix, to be appended to a baseUrl. It is not a full URL.
 */
fun defaultTypeBuilder(status: Status): String {
    if (status.origin == StatusConstants.KIIT) return "?code=${status.code}#codes"

    val scope = status.scope.split('.').filter { it.isNotEmpty() }
    return (scope + status.code.split(':')).joinToString("/")
}
