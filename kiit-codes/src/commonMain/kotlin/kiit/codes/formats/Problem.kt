/** url: www.kiit.dev */
package kiit.codes.formats

/**
 * kiit-side name for RFC 9457's "problem details object"
 * (https://www.rfc-editor.org/rfc/rfc9457.html).
 *
 * ```json
 * {
 *     "type": "https://stripe.com/docs/codes/payments/cards/Failed/Rejected/DUPLICATE_CHARGE",
 *     "title": "This charge has already been processed",
 *     "status": 409,
 *     "code": "stripe.com:payments.cards:Failed:Rejected:DUPLICATE_CHARGE"
 * }
 * ```
 *
 * 1. Opt-in output shape, produce one via [ProblemConverter].
 * 2. For internal service-to-service calls or anywhere else an HTTP status/URI doesn't apply,
 *    see [CodeDetail], kiit-codes' own native equivalent.
 * 3. [errors] and [code] aren't RFC 9457's own members (`type`/`title`/`status`/`detail`/`instance`) —
 *    they are kiit extensions, allowed under the spec's own provision for extension members. Clients
 *    that don't know them ignore them.
 * 4. [type] is the problem's identity and its docs pointer, compared as an opaque string. [code] is the exact kiit
 *    identity, the same `{origin}:{scope}:{Status.code}` string as [CodeDetail.code], so a client reads the parts
 *    from it instead of parsing [type]. [ProblemConverter] always sets it. It is nullable with a `null`
 *    default so a [Problem] built by hand still compiles.
 * 5. The tail of a custom [type] is `{scope}/{code}` in exact case, where `{code}` is `status/group/name`, the
 *    same values as [Status.code] joined with `/`. It is a readable form of what [code] holds, not something
 *    RFC 9457 expects consumers to parse, and it leaves out the origin.
 */
data class Problem<T : ErrorItem>(
    val type: String,
    val title: String,
    val status: Int,
    val detail: String? = null,
    val instance: String? = null,
    val errors: List<T>? = null,
    val code: String? = null,
)
