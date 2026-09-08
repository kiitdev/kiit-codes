/** url: www.kiit.dev */
@file:OptIn(ExperimentalJsExport::class)
@file:JvmName("Problems")

package kiit.codes

import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.jvm.JvmName
import kotlin.jvm.JvmOverloads

/**
 * kiit-side name for RFC 9457's "problem details object"
 * (https://www.rfc-editor.org/rfc/rfc9457.html). Opt-in output shape, never returned by default
 * from anything — build one explicitly via [toProblemDetail].
 *
 * Shape:
 * ```json
 * {
 *      "type"   : "https://stripe.com/problems/payments.cards/rejected/duplicate-charge",
 *      "title"  : "This charge has already been processed",
 *      "status" : 409
 * }
 * ```
 *
 * For internal service-to-service calls, background jobs, or anywhere else an HTTP status code
 * and a URI `type` don't mean anything, see [CodeDetail] — kiit-codes' own native equivalent,
 * built from [Status.path]/[Status.code] rather than converted to RFC 9457's vocabulary.
 */
@JsExport
data class ProblemDetail(
    val type: String,
    val title: String,
    val status: Int,
    val detail: String? = null,
    val instance: String? = null,
    val errors: List<ProblemError>? = null,
)

/**
 * kiit-side name for one entry in RFC 9457's `errors` extension. [field] is null when the source
 * [Err] isn't field-scoped, see [toProblemDetail].
 */
@JsExport
data class ProblemError(
    val field: String?,
    val detail: String,
)

/**
 * Default [toProblemDetail] `type` construction: the relative path segment of the URI (everything
 * after `baseUrl`), lowercase-dash transformed. Includes [Status.group] — the confirmed
 * "near-superset" addition RFC 9457 has no equivalent for — so two codes sharing [Status.scope]/
 * [Status.name] but differing in [Status.group] don't collide here either. [Status.scope] is
 * included only when non-empty.
 *
 * Deliberately excludes [Status.origin]: [toProblemDetail] already requires [baseUrl] to be
 * specific to one origin (defaulted only for kiit-codes' own built-ins, required explicitly for
 * every other origin), so restating it in the path would just repeat what the host in [baseUrl]
 * already says, e.g. `https://stripe.com/problems/com.stripe/...` naming Stripe twice.
 *
 * Pass a different function as `toProblemDetail`'s `typeBuilder` to override this entirely, e.g.
 * for a consumer with their own, already-stable URI scheme for problem types, or one whose
 * `baseUrl` deliberately aggregates more than one origin and needs origin back in the path.
 */
fun defaultTypeBuilder(status: Status): String {
    fun String.toUriSegment() = lowercase().replace("_", "-")
    val segments = listOfNotNull(status.scope.ifEmpty { null }, status.group, status.name)
    return segments.joinToString("/") { it.toUriSegment() }
}

/**
 * [toProblemDetail]'s `baseUrl` default, honest only for kiit-codes' own [origin] — see
 * [toProblemDetail]'s KDoc for why every other origin must supply its own.
 */
private fun defaultBaseUrlFor(origin: String): String =
    if (origin == StatusConstants.KIIT) {
        "https://kiit.dev/problems"
    } else {
        throw IllegalArgumentException(
            "baseUrl is required for origin '$origin', kiit-codes only owns " +
                "documentation for its own built-ins.",
        )
    }

/**
 * Builds an RFC 9457 [ProblemDetail] for [status] (and optionally [err]) — the exact structure
 * https://www.rfc-editor.org/rfc/rfc9457.html describes. [status] and [Status] itself are never
 * modified.
 *
 * [baseUrl] is only defaulted for kiit-codes' own built-ins ([StatusConstants.KIIT] origin) —
 * `kiit-codes` genuinely documents those. Every other [Status.origin] (a consumer's own, or a
 * future extension) must supply [baseUrl] explicitly; a silent, plausible-looking default
 * pointing nowhere real would be worse than no default, so this throws instead. That's a clear,
 * developer-time configuration error at this converter's own boundary, not a runtime invariant on
 * [Status] itself — [Status.scope] and the rest of [Status]'s fields still carry no runtime
 * validation of their own, by design, for consistent behavior across every KMP target.
 *
 * [typeBuilder] (default [defaultTypeBuilder]) controls how `type`'s path segment is built from
 * [status]; [baseUrl] is always prepended to its result.
 *
 * Field mapping:
 * - [baseUrl] + [typeBuilder] -> [ProblemDetail.type].
 * - [Status.message] -> [ProblemDetail.title].
 * - [CodesToHttp.toCode] -> [ProblemDetail.status].
 * - [Err.message] -> [ProblemDetail.detail] (occurrence-specific, never on [status] itself).
 * - [Err.ref] -> [ProblemDetail.instance].
 * - [Err.ErrorList.errors] -> [ProblemDetail.errors], any other [Err] shape leaves it null.
 *
 * Not `@JsExport`ed directly — a function-typed parameter like [typeBuilder] isn't representable
 * in Kotlin/JS's export surface. JS/TS consumers needing the default construction only, without
 * the override, can use [problemDetailFor] instead.
 */
@JvmOverloads
fun toProblemDetail(
    status: Status,
    err: Err? = null,
    baseUrl: String? = null,
    typeBuilder: (Status) -> String = ::defaultTypeBuilder,
): ProblemDetail {
    val resolvedBaseUrl = baseUrl ?: defaultBaseUrlFor(status.origin)
    val type = "$resolvedBaseUrl/${typeBuilder(status)}"
    val code = CodesToHttp().toCode(status)

    return when (err) {
        is Err.ErrorList ->
            ProblemDetail(
                type = type,
                title = status.message,
                status = code,
                detail = err.message,
                errors = err.errors.map { ProblemError((it as? Err.ErrorField)?.field, it.message) },
            )
        else ->
            ProblemDetail(
                type = type,
                title = status.message,
                status = code,
                detail = err?.message,
                instance = err?.ref?.toString(),
            )
    }
}

/**
 * JS/TS-reachable proxy for [toProblemDetail] using the default [defaultTypeBuilder] construction
 * (no override), since a function-typed parameter isn't representable in Kotlin/JS's export
 * surface, see [Codes]'s KDoc for why these proxies exist generally.
 */
@JsExport
fun problemDetailFor(status: Status, err: Err? = null, baseUrl: String? = null): ProblemDetail {
    return toProblemDetail(status, err, baseUrl)
}
