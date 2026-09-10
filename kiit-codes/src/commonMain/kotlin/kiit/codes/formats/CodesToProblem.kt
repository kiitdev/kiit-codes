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
    fun build(status: Status, err: Err? = null, typeBuilder: (Status) -> String = ::defaultTypeBuilder): Problem<ErrorDetail> =
        buildCustom(status, err, ::defaultErrorItem, typeBuilder)

    /**
     * Builds a [Problem]\<[T]\>, mapping each [Err.ErrorList] entry through [mapper]. Use this for
     * anything richer than field + message, see [ErrorItem]. Named differently from [build]
     * (not an overload) since both take a trailing function parameter — Kotlin can't tell a
     * `build(status, err) { ... }` call apart from one meant for [build]'s own `typeBuilder`.
     */
    fun <T : ErrorItem> buildCustom(
        status: Status,
        err: Err?,
        mapper: (Err) -> T,
        typeBuilder: (Status) -> String = ::defaultTypeBuilder,
    ): Problem<T> {
        val baseUrl = catalog.baseUrlFor(status.origin)
            ?: throw IllegalArgumentException(
                "No baseUrl registered for origin '${status.origin}'. Register one via Catalog.register().",
            )
        return toProblem(status, err, baseUrl, mapper, typeBuilder, mapping)
    }

    /** Same as [build], but [baseUrl] is supplied directly instead of looked up from [catalog]. */
    @JvmOverloads
    fun convert(
        status: Status,
        err: Err? = null,
        baseUrl: String,
        typeBuilder: (Status) -> String = ::defaultTypeBuilder,
    ): Problem<ErrorDetail> = convertCustom(status, err, baseUrl, ::defaultErrorItem, typeBuilder)

    /** Generic form of [convert], mapping each [Err.ErrorList] entry through [mapper]. */
    fun <T : ErrorItem> convertCustom(
        status: Status,
        err: Err?,
        baseUrl: String,
        mapper: (Err) -> T,
        typeBuilder: (Status) -> String = ::defaultTypeBuilder,
    ): Problem<T> = toProblem(status, err, baseUrl, mapper, typeBuilder, mapping)
}

private fun <T : ErrorItem> toProblem(
    status: Status,
    err: Err?,
    baseUrl: String,
    mapper: (Err) -> T,
    typeBuilder: (Status) -> String,
    mapping: CodesToHttp,
): Problem<T> {
    val type = "$baseUrl/${typeBuilder(status)}"
    val code = mapping.toCode(status)
    return when (err) {
        is Err.ErrorList ->
            Problem(type = type, title = status.message, status = code, detail = err.message, errors = err.errors.map(mapper))
        else ->
            Problem(type = type, title = status.message, status = code, detail = err?.message, instance = err?.ref?.toString())
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
