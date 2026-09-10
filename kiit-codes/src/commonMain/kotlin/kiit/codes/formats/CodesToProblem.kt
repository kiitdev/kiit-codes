/** url: www.kiit.dev */
@file:OptIn(ExperimentalJsExport::class)

package kiit.codes.formats

import kiit.codes.CodesToHttp
import kiit.codes.Err
import kiit.codes.Status
import kotlin.js.ExperimentalJsExport
import kotlin.js.JsExport
import kotlin.jvm.JvmOverloads

/**
 * Converts a [Status] into an RFC 9457 [Problem], the way [kiit.codes.CodesToHttp]/
 * [kiit.codes.CodesToGrpc] convert one into a protocol code. [catalog] supplies `baseUrl` per
 * origin (see [Catalog]); [mapping] is reused rather than a fresh [CodesToHttp] per call.
 *
 * Not `@JsExport`ed: every method here takes a `typeBuilder` function, and a function-typed
 * parameter isn't representable in Kotlin/JS's export surface. Use [problemFor] from JS/TS.
 */
class CodesToProblem(private val catalog: Catalog, private val mapping: CodesToHttp) {
    /** Builds a [Problem]\<[kiit.codes.formats.ErrorDetail]\> for [status], baseUrl from [catalog]. */
    @JvmOverloads
    fun build(
        status: Status,
        err: Err? = null,
        typeBuilder: (Status) -> String = ::defaultTypeBuilder,
    ): Problem<ErrorDetail> {
        return buildCustom(status, err, typeBuilder, ::defaultErrorItem)
    }

    /**
     * Builds a [Problem]\<[T]\>, mapping each [Err.ErrorList] entry through [mapper]. Use this for
     * anything richer than field + message, see [ErrorItem]. Named differently from [build]
     * (not an overload) since both take a trailing function parameter — Kotlin can't tell a
     * `build(status, err) { ... }` call apart from one meant for [build]'s own `typeBuilder`.
     *
     * [mapper] comes last (after the defaulted [typeBuilder]) so it can be passed as a trailing
     * lambda — Kotlin's trailing-lambda syntax always binds to a function's last parameter.
     */
    fun <T : ErrorItem> buildCustom(
        status: Status,
        err: Err?,
        typeBuilder: (Status) -> String = ::defaultTypeBuilder,
        mapper: (Err) -> T,
    ): Problem<T> {
        val baseUrl =
            catalog.baseUrlFor(status.origin)
                ?: throw IllegalArgumentException(
                    "No baseUrl registered for origin '${status.origin}'. Register one via Catalog.register().",
                )
        return toProblem(status, err, baseUrl, typeBuilder, mapper)
    }

    /** Same as [build], but [baseUrl] is supplied directly instead of looked up from [catalog]. */
    @JvmOverloads
    fun convert(
        status: Status,
        err: Err? = null,
        baseUrl: String,
        typeBuilder: (Status) -> String = ::defaultTypeBuilder,
    ): Problem<ErrorDetail> {
        return convertCustom(status, err, baseUrl, typeBuilder, ::defaultErrorItem)
    }

    /** Generic form of [convert], mapping each [Err.ErrorList] entry through [mapper]. */
    fun <T : ErrorItem> convertCustom(
        status: Status,
        err: Err?,
        baseUrl: String,
        typeBuilder: (Status) -> String = ::defaultTypeBuilder,
        mapper: (Err) -> T,
    ): Problem<T> = toProblem(status, err, baseUrl, typeBuilder, mapper)

    private fun <T : ErrorItem> toProblem(
        status: Status,
        err: Err?,
        baseUrl: String,
        typeBuilder: (Status) -> String,
        mapper: (Err) -> T,
    ): Problem<T> {
        val type = "$baseUrl/${typeBuilder(status)}"
        val code = mapping.toCode(status)
        return when (err) {
            is Err.ErrorList ->
                Problem(
                    type = type,
                    title = status.message,
                    status = code,
                    detail = err.message,
                    errors = err.errors.map(mapper),
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
}

/**
 * Default `type` path segment (everything after `baseUrl`): `scope`/`group`/`name`, lowercase-
 * dash. `origin` is left out on purpose, `baseUrl` is already specific to one origin (see
 * [CodesToProblem]), so repeating it would just name that origin twice in the URL.
 */
fun defaultTypeBuilder(status: Status): String {
    fun String.toUriSegment() = lowercase().replace("_", "-")
    val segments = listOfNotNull(status.scope.ifEmpty { null }, status.group, status.name)
    return segments.joinToString("/") { it.toUriSegment() }
}

/** JS/TS-reachable proxy for [CodesToProblem.build], default [ErrorDetail] shape only. */
@JsExport
fun problemFor(catalog: Catalog, mapping: CodesToHttp, status: Status, err: Err? = null): Problem<ErrorDetail> =
    CodesToProblem(catalog, mapping).build(status, err)
