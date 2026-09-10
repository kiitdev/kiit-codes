package kiit.codes.formats

import kiit.codes.CodesToHttp
import kiit.codes.Err
import kiit.codes.Status
import kiit.codes.StatusConstants
import kotlin.jvm.JvmOverloads

/**
 * Builds an RFC 9457 [Problem] using [kiit.codes.Status] (and optionally [kiit.codes.Err])
 * Refer to : https://www.rfc-editor.org/rfc/rfc9457.html
 */
class Problems(private val catalog: Catalog,
                       private val mapping: CodesToHttp
)  {

   fun build(status: Status, err: Err?): Problem {
        val baseUrl = catalog.baseUrlFor(status.origin)
            ?: throw IllegalArgumentException(
                "No baseUrl registered for origin '${status.origin}'. Register one via " +
                    "CodeCatalog.register()."
            )
        val code = mapping.toCode(status)
        val type = baseUrl
        return when (err) {
            is Err.ErrorList ->
                Problem(
                    type = type,
                    title = status.message,
                    status = code,
                    detail = err.message,
                    errors = err.errors.map { ErrorDetail((it as? Err.ErrorField)?.field, it.message) },
                )

            else ->
                Problem(
                    type = type,
                    title = status.message,
                    status = code,
                    detail = err?.message,
                    instance = err?.ref?.toString(),
                )
        }
    }


    /**
     * Builds an RFC 9457 [Problem] for [status] (and optionally [err]), the exact structure
     * https://www.rfc-editor.org/rfc/rfc9457.html describes. [status] and [Status] itself are never
     * modified.
     */
    @JvmOverloads
    fun convert(
        status: Status,
        err: Err? = null,
        baseUrl: String,
        typeBuilder: (Status) -> String = ::defaultTypeBuilder,
    ): Problem {
        val type = "$baseUrl/${typeBuilder(status)}"
        val code = CodesToHttp().toCode(status)

        return when (err) {
            is Err.ErrorList ->
                Problem(
                    type = type,
                    title = status.message,
                    status = code,
                    detail = err.message,
                    errors = err.errors.map { ErrorDetail((it as? Err.ErrorField)?.field, it.message) },
                )
            else ->
                Problem(
                    type = type,
                    title = status.message,
                    status = code,
                    detail = err?.message,
                    instance = err?.ref?.toString(),
                )
        }
    }


    /**
     * Default [toProblemDetail] `type` construction: the relative path segment of the URI
     * (everything after `baseUrl`), lowercase-dash transformed. It includes [Status.group], the
     * "near-superset" addition RFC 9457 has no equivalent for, so two codes sharing
     * [Status.scope]/[Status.name] but differing in [Status.group] still won't collide.
     * [Status.scope] is included only when non-empty.
     *
     * It leaves out [Status.origin] on purpose. [toProblemDetail] already requires [baseUrl] to be
     * specific to one origin (defaulted only for kiit-codes' own built-ins, required explicitly for
     * every other origin), so restating origin in the path would just repeat what the host in
     * [baseUrl] already says, e.g. `https://stripe.com/problems/com.stripe/...` naming Stripe twice.
     *
     * Pass a different function as `toProblemDetail`'s `typeBuilder` to override this entirely, e.g.
     * for a consumer with their own, already-stable URI scheme for problem types, or one whose
     * `baseUrl` aggregates more than one origin and needs origin back in the path.
     */
    fun defaultTypeBuilder(status: Status): String {
        fun String.toUriSegment() = lowercase().replace("_", "-")
        val segments = listOfNotNull(status.scope.ifEmpty { null }, status.group, status.name)
        return segments.joinToString("/") { it.toUriSegment() }
    }
}


