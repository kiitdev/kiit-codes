package sample

import kiit.codes.*

/**
 * Prints the docs table "Reference > Protocol mappings" (kiit-site, docs/foundations/kiit-codes.md): every built-in code
 * with its HTTP and gRPC code, computed from [CodesToHttp] and [CodesToGrpc] over [Codes.all]. Nothing is typed by
 * hand, so the table can't disagree with the library. Run it after a code or mapping change and paste the output.
 *
 * Run with `./gradlew -q :sample-kotlin:printMappingTable`.
 */

private val HTTP_NAMES =
    mapOf(
        200 to "OK",
        201 to "Created",
        202 to "Accepted",
        204 to "No Content",
        307 to "Temporary Redirect",
        400 to "Bad Request",
        401 to "Unauthorized",
        403 to "Forbidden",
        404 to "Not Found",
        409 to "Conflict",
        410 to "Gone",
        413 to "Payload Too Large",
        423 to "Locked",
        429 to "Too Many Requests",
        451 to "Unavailable For Legal Reasons",
        499 to "Client Closed Request",
        500 to "Internal Server Error",
        501 to "Not Implemented",
        503 to "Service Unavailable",
        504 to "Gateway Timeout",
    )

private val GRPC_NAMES =
    mapOf(
        0 to "OK",
        1 to "CANCELLED",
        2 to "UNKNOWN",
        3 to "INVALID_ARGUMENT",
        4 to "DEADLINE_EXCEEDED",
        5 to "NOT_FOUND",
        6 to "ALREADY_EXISTS",
        7 to "PERMISSION_DENIED",
        8 to "RESOURCE_EXHAUSTED",
        9 to "FAILED_PRECONDITION",
        10 to "ABORTED",
        11 to "OUT_OF_RANGE",
        12 to "UNIMPLEMENTED",
        13 to "INTERNAL",
        14 to "UNAVAILABLE",
        15 to "DATA_LOSS",
        16 to "UNAUTHENTICATED",
    )

fun main() {
    val http = CodesToHttp()
    val grpc = CodesToGrpc()
    println("| Group | Code | HTTP | gRPC |")
    println("|---|---|---|---|")
    var lastGroup = ""
    for (status in Codes.all) {
        val group = if (status.group == lastGroup) "" else "<GroupBadge group=\"${status.group}\" />"
        lastGroup = status.group
        val h = http.toCode(status)
        val g = grpc.toCode(status)
        val httpName = HTTP_NAMES[h] ?: error("No name for HTTP $h, add it to HTTP_NAMES")
        val grpcName = GRPC_NAMES[g] ?: error("No name for gRPC $g, add it to GRPC_NAMES")
        println("| $group | <CodeBadge>${status.name}</CodeBadge> | $h $httpName | $g $grpcName |")
    }
}
