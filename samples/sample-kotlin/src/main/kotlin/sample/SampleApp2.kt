package sample

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kiit.codes.*
import kiit.codes.formats.*
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray

/**
 * Canonical sample for kiit-codes (becomes SampleApp.kt once reviewed). One running example, a to-do list,
 * in five parts: 1. Overview, 2. Taxonomy, 3. Usage, 4. Protocols, 5. Problem details.
 *
 * 1. Every example is wrapped in `// <example id="..." tags="...">` ... `// </example>` so it can be extracted
 *    for the docs and blog. The `verify(...)` calls sit outside the markers, they check the example still works.
 * 2. The other samples (Java, TypeScript, Swift) will match this one.
 * 3. Run it with `./gradlew :sample-kotlin:runSample2`.
 */

/*
<example id="setup-install" tags="setup">
```kotlin title="build.gradle.kts"
dependencies {
    implementation("{{module.group}}:{{module.artifact}}:{{module.version}}")
}
```
</example>
*/

private const val ORIGIN = "samples.kiit.dev"

// ============================================================
// Domain: a minimal to-do list, the one running example used across every part below
// ============================================================

data class Task(val title: String, val listId: String)

/** Custom codes for the to-do list. Constant, they only say what kind of outcome it is. */
object TaskCodes {
    val EMPTY_TITLE = Invalid(name = "EMPTY_TITLE", message = "Title must not be empty", origin = ORIGIN)
    val DUPLICATE_TASK =
        Rejected(name = "DUPLICATE_TASK", message = "A task with this title exists", origin = ORIGIN, scope = "lists.team")
}

class TaskService {
    companion object {
        const val PERSONAL_LIST = "personal"
        const val TEAM_LIST = "team"
    }

    private val listOwners = mapOf(PERSONAL_LIST to "amy", TEAM_LIST to "erin")
    private val existingTitles = mutableSetOf("groceries")

    /** The spine: built-in outcomes, plus one custom code. */
    fun create(title: String, listId: String = PERSONAL_LIST): Status {
        if (title.isBlank()) return TaskCodes.EMPTY_TITLE
        if (title in existingTitles) return Rejected.CONFLICT
        existingTitles += title
        return Succeeded.CREATED
    }

    fun exists(title: String): Boolean = title in existingTitles

    /** Only the list owner can complete a task. */
    fun complete(title: String, listId: String, requesterId: String): Status {
        if (title !in existingTitles) return Rejected.NOT_EXISTS
        if (listOwners[listId] != requesterId) return Restricted.FORBIDDEN
        return Succeeded.SUCCESS
    }

    /** Adding to the team list waits for the team owner to accept it. */
    fun addToTeamList(title: String): Status = if (title.isBlank()) TaskCodes.EMPTY_TITLE else Pending.QUEUED

    /** One status per title: created, skipped (blank) or deduplicated (already exists). */
    fun importTasks(titles: List<String>): List<Status> =
        titles.map { title ->
            when {
                title.isBlank() -> Excluded.SKIPPED
                title in existingTitles -> Excluded.DEDUPLICATED
                else -> {
                    existingTitles += title
                    Succeeded.CREATED
                }
            }
        }

    /** A boundary that can only communicate via exceptions. */
    fun createTaskOrThrow(title: String, listId: String = PERSONAL_LIST): Task {
        val status = create(title, listId)
        if (status is Failed) throw status.toException()
        return Task(title, listId)
    }
}

/** Two independent field checks, kept separate so Part 3 can combine them with collect(). */
class TaskValidator {
    fun validateTitle(title: String): Checked =
        if (title.isNotBlank() && title.length <= 100) {
            Checked.success()
        } else {
            Checked.failure(Invalid.BAD_REQUEST, listOf(Err.on("title", title, "must be 1-100 characters")))
        }

    fun validateListId(listId: String): Checked =
        if (listId in setOf(TaskService.PERSONAL_LIST, TaskService.TEAM_LIST)) {
            Checked.success()
        } else {
            Checked.failure(Invalid.NOT_FOUND, listOf(Err.on("listId", listId, "unknown list")))
        }
}

// ============================================================
// Helpers
// ============================================================

private var checks = 0

/** Fails the run if a claim an example makes stops being true. Outside the example markers on purpose. */
private fun verify(label: String, condition: Boolean) {
    check(condition) { "FAILED: $label" }
    checks++
    println("  ok: $label")
}

private fun section(title: String) {
    println()
    println("=".repeat(60))
    println(title)
    println("=".repeat(60))
}

// ============================================================
// Part 1: Overview
// ============================================================

fun showOverview(tasks: TaskService) {
    section("Part 1: Overview")

    // <example id="overview-tiers" tags="concepts">
    // A Status is the outcome of any operation, at any layer. Like an http code, but general.
    // 1. It tells you the KIND of success or failure (e.g. a security failure is Failed.Restricted)
    // 2. Details of what went wrong are separate, see Part 3
    //
    // Status  = Passed     | Failed
    // Passed  = Succeeded  | Pending  | Excluded  | Information
    // Failed  = Restricted | Invalid  | Rejected  | Unserved
    // </example>

    // <example id="overview-usage" tags="concepts">
    // A small example using a custom code and two built-in codes
    fun validate(title: String): Status =
        when {
            title.isEmpty() -> TaskCodes.EMPTY_TITLE
            tasks.exists(title) -> Rejected.CONFLICT
            else -> Succeeded.SUCCESS
        }

    val validated = validate("Get groceries") // Succeeded.SUCCESS
    // </example>
    verify("overview-usage: new title", validated == Succeeded.SUCCESS)
    verify("overview-usage: existing title", validate("groceries") == Rejected.CONFLICT)
    verify("overview-usage: empty title", validate("") == TaskCodes.EMPTY_TITLE)

    // <example id="overview-shape" tags="concepts">
    // Every status carries the same fields, built-in or custom
    val status: Status = Succeeded.SUCCESS
    println("name=${status.name} group=${status.group} origin=${status.origin} scope='${status.scope}'")
    println("success=${status.success} message=${status.message}")
    // name=SUCCESS group=Succeeded origin=kiit.dev scope=''
    // success=true message=The operation completed successfully.
    // </example>
    verify("overview-shape: name", status.name == "SUCCESS")
    verify("overview-shape: group", status.group == "Succeeded")
    verify("overview-shape: origin", status.origin == StatusConstants.KIIT && status.origin == "kiit.dev")
    verify("overview-shape: scope", status.scope == "")
    verify("overview-shape: success", status.success)
    verify("overview-shape: message", status.message == "The operation completed successfully.")

    // <example id="overview-checks" tags="concepts">
    // Check a status with .success (simplest), or with the two branches (Passed | Failed)
    val outcome = tasks.create("buy milk")
    if (outcome.success) println("created: ${outcome.name}")
    when (outcome) {
        is Passed -> println("passed: ${outcome.name}")
        is Failed -> println("failed: ${outcome.name}, ${outcome.message}")
    }
    // </example>
    verify("overview-checks: .success and Passed agree", outcome.success && outcome is Passed)
}

// ============================================================
// Part 2: Taxonomy
// ============================================================

fun showTaxonomy(tasks: TaskService) {
    section("Part 2: Taxonomy")

    // <example id="taxonomy-groups" tags="concepts">
    // Tier 1 is Passed | Failed, tier 2 is the 8 groups, tier 3 is the individual codes.
    // Both `when` blocks have no `else`, the compiler checks every group is handled.
    fun groupOf(status: Status): String =
        when (status) {
            is Passed ->
                when (status) {
                    is Passed.Succeeded -> "Succeeded"
                    is Passed.Pending -> "Pending"
                    is Passed.Excluded -> "Excluded"
                    is Passed.Information -> "Information"
                }
            is Failed ->
                when (status) {
                    is Failed.Restricted -> "Restricted"
                    is Failed.Invalid -> "Invalid"
                    is Failed.Rejected -> "Rejected"
                    is Failed.Unserved -> "Unserved"
                }
        }
    // </example>
    verify("taxonomy-groups: Succeeded", groupOf(Succeeded.CREATED) == "Succeeded")
    verify("taxonomy-groups: Pending", groupOf(Pending.QUEUED) == "Pending")
    verify("taxonomy-groups: Excluded", groupOf(Excluded.SKIPPED) == "Excluded")
    verify("taxonomy-groups: Restricted", groupOf(Restricted.FORBIDDEN) == "Restricted")
    verify("taxonomy-groups: Invalid", groupOf(TaskCodes.EMPTY_TITLE) == "Invalid")
    verify("taxonomy-groups: Rejected", groupOf(Rejected.CONFLICT) == "Rejected")

    // <example id="taxonomy-defaults" tags="concepts,defaults">
    // Each of the 8 groups has a default code, available as DEFAULT. It is an alias, the same instance.
    val defaults: List<Status> =
        listOf(
            Succeeded.DEFAULT, Pending.DEFAULT, Excluded.DEFAULT, Information.DEFAULT,
            Restricted.DEFAULT, Invalid.DEFAULT, Rejected.DEFAULT, Unserved.DEFAULT,
        )
    defaults.forEach { println("${it.group}: ${it.name}") }
    // Succeeded: SUCCESS, Pending: ACCEPTED, Excluded: OMITTED, Information: NOTICE,
    // Restricted: DENIED, Invalid: INVALID_VALUE, Rejected: RULE_VIOLATION, Unserved: UNEXPECTED

    println(Invalid.DEFAULT === Invalid.INVALID_VALUE) // true, not a new code
    println(Invalid.INVALID_VALUE.isDefault) // true
    println(Invalid.BAD_REQUEST.isDefault) // false
    println(Invalid.DEFAULT.copy(message = "custom").isDefault) // false, isDefault compares every field
    // </example>
    verify(
        "taxonomy-defaults: names",
        defaults.map { it.name } ==
            listOf(
                "SUCCESS", "ACCEPTED", "OMITTED", "NOTICE", "DENIED", "INVALID_VALUE", "RULE_VIOLATION", "UNEXPECTED",
            ),
    )
    verify("taxonomy-defaults: all isDefault", defaults.all { it.isDefault })
    verify("taxonomy-defaults: alias is the same instance", Invalid.DEFAULT === Invalid.INVALID_VALUE)
    verify("taxonomy-defaults: non-default", !Invalid.BAD_REQUEST.isDefault)
    verify("taxonomy-defaults: changed copy", !Invalid.DEFAULT.copy(message = "custom").isDefault)

    // <example id="taxonomy-builtin" tags="concepts">
    // Built-in codes from Pending and Excluded, not only Succeeded and Failed ones
    val queued = tasks.addToTeamList("plan offsite") // waits for the team owner
    val imported = tasks.importTasks(listOf("standup notes", "", "groceries"))
    imported.forEach { println("import: ${it.group} ${it.name}") }
    // import: Succeeded CREATED, import: Excluded SKIPPED, import: Excluded DEDUPLICATED
    // </example>
    verify("taxonomy-builtin: pending", queued == Pending.QUEUED)
    verify(
        "taxonomy-builtin: import",
        imported == listOf(Succeeded.CREATED, Excluded.SKIPPED, Excluded.DEDUPLICATED),
    )

    // <example id="taxonomy-custom" tags="concepts,origin">
    // Create your own codes in any of the 8 groups. Constant, they only say what kind of outcome it is.
    val missingDate = Invalid(name = "MISSING_DATE", message = "Date not supplied", origin = "samples.kiit.dev")
    val duplicate =
        Rejected(name = "DUPLICATE_TASK", message = "A task with this title exists", origin = "samples.kiit.dev", scope = "lists.team")
    println("${missingDate.group}: ${missingDate.name} origin=${missingDate.origin} scope='${missingDate.scope}'")
    println("${duplicate.group}: ${duplicate.name} origin=${duplicate.origin} scope='${duplicate.scope}'")
    // Invalid: MISSING_DATE origin=samples.kiit.dev scope=''
    // Rejected: DUPLICATE_TASK origin=samples.kiit.dev scope='lists.team'
    // </example>
    verify("taxonomy-custom: invalid", missingDate.group == "Invalid" && missingDate.scope == "")
    verify("taxonomy-custom: rejected", duplicate.group == "Rejected" && duplicate.scope == "lists.team")
    verify("taxonomy-custom: origin", missingDate.origin == "samples.kiit.dev" && duplicate.origin == "samples.kiit.dev")

    // <example id="taxonomy-origin" tags="origin">
    // An origin is a real domain you own, or any other id. It also becomes the host of the RFC 9457 type (Part 5).
    val withDomain = Rejected(name = "OUT_OF_STOCK", message = "Out of stock", origin = "samples.kiit.dev")
    val withPlainId = Rejected(name = "OUT_OF_STOCK", message = "Out of stock", origin = "myapp1")

    // A domain is unique through DNS. A plain id can collide with another team's "myapp1", and kiit-codes
    // can't detect that. Pick a specific name when you have no domain.
    // </example>
    verify("taxonomy-origin: domain", withDomain.origin == "samples.kiit.dev")
    verify("taxonomy-origin: plain id", withPlainId.origin == "myapp1")
}

// ============================================================
// Part 3: Usage
// ============================================================

fun showUsage(tasks: TaskService, validator: TaskValidator) {
    section("Part 3: Usage")

    // <example id="usage-err" tags="usage">
    // The kind of failure (Status) is separate from the details of this failure (Err)
    // 1. Kind     : Restricted | Invalid     | Rejected   | Unserved
    // 2. Details  : ErrorInfo  | ErrorField  | ErrorList
    val fieldErr = Err.on("title", "", "must be 1-100 characters")
    val kind =
        when (fieldErr) {
            is Err.ErrorInfo -> "ErrorInfo"
            is Err.ErrorField -> "ErrorField"
            is Err.ErrorList -> "ErrorList"
        }
    val detail = ErrorDetail(field = (fieldErr as? Err.ErrorField)?.field, message = fieldErr.message)
    println("kind=$kind detail=$detail") // kind=ErrorField detail=ErrorDetail(field=title, message=must be 1-100 characters)
    // </example>
    verify("usage-err: kind", kind == "ErrorField")
    verify("usage-err: detail", detail == ErrorDetail(field = "title", message = "must be 1-100 characters"))

    // <example id="usage-status" tags="usage">
    // A status on its own, when the outcome is all the caller needs
    val status = tasks.create("read a book")
    println("status: ${status.name}") // status: CREATED
    // </example>
    verify("usage-status: created", status == Succeeded.CREATED)

    // <example id="usage-checked" tags="usage">
    // Combine independent checks with collect(), all the errors come back together
    val combined = collect(validator.validateTitle(""), validator.validateListId("unknown-list"))
    println("valid=${combined.isValid} errors=${combined.errors.size}") // valid=false errors=2
    // </example>
    verify("usage-checked: invalid", !combined.isValid)
    verify("usage-checked: two errors", combined.errors.size == 2)

    // <example id="usage-exception" tags="usage">
    // For a boundary that can only communicate with exceptions, catch the specific subclass
    try {
        tasks.createTaskOrThrow("groceries") // already exists, so Rejected.CONFLICT
    } catch (e: StatusException.RejectedException) {
        println("caught: ${e.status.name}, ${e.message}") // caught: CONFLICT, The request conflicts with the current state.
    }
    // </example>
    val thrown = runCatching { tasks.createTaskOrThrow("groceries") }.exceptionOrNull()
    verify("usage-exception: subclass", thrown is StatusException.RejectedException)
    verify("usage-exception: status", (thrown as? StatusException)?.status == Rejected.CONFLICT)
}

// ============================================================
// Part 4: Protocols
// ============================================================

fun showProtocols() {
    section("Part 4: Protocols")

    // <example id="conversion-http" tags="conversion">
    // One status maps to one http code. The reverse isn't available, many statuses share a code.
    val http = CodesToHttp()
    println(http.toCode(Succeeded.CREATED)) // 201
    println(http.toCode(Rejected.CONFLICT)) // 409
    println(http.toCode(Restricted.FORBIDDEN)) // 403
    println(http.toCode(Pending.QUEUED)) // 202
    println(http.toCode(Excluded.DEDUPLICATED)) // 200
    // </example>
    verify("conversion-http: created", http.toCode(Succeeded.CREATED) == 201)
    verify("conversion-http: conflict", http.toCode(Rejected.CONFLICT) == 409)
    verify("conversion-http: forbidden", http.toCode(Restricted.FORBIDDEN) == 403)
    verify("conversion-http: queued", http.toCode(Pending.QUEUED) == 202)
    verify("conversion-http: deduplicated", http.toCode(Excluded.DEDUPLICATED) == 200)

    // <example id="conversion-grpc" tags="conversion">
    // The same statuses as gRPC codes
    val grpc = CodesToGrpc()
    println(grpc.toCode(Succeeded.CREATED)) // 0, OK
    println(grpc.toCode(Rejected.CONFLICT)) // 6, ALREADY_EXISTS
    println(grpc.toCode(Restricted.FORBIDDEN)) // 7, PERMISSION_DENIED
    println(grpc.toCode(Invalid.NOT_FOUND)) // 5, NOT_FOUND
    // </example>
    verify("conversion-grpc: created", grpc.toCode(Succeeded.CREATED) == 0)
    verify("conversion-grpc: conflict", grpc.toCode(Rejected.CONFLICT) == 6)
    verify("conversion-grpc: forbidden", grpc.toCode(Restricted.FORBIDDEN) == 7)
    verify("conversion-grpc: not found", grpc.toCode(Invalid.NOT_FOUND) == 5)
}

// ============================================================
// Part 5: Problem details (RFC 9457)
// ============================================================

/** A Problem as JSON with Jackson: serializes the data class directly, no mapping. */
private val jackson =
    jacksonObjectMapper()
        .setSerializationInclusion(JsonInclude.Include.NON_NULL)
        .enable(SerializationFeature.INDENT_OUTPUT)

private fun Any.toJson(): String = jackson.writeValueAsString(this)

/** The same Problem with kotlinx.serialization. kiit-codes classes aren't @Serializable, so it is mapped by hand. */
private fun Problem<ErrorDetail>.toKotlinxJson(): JsonObject =
    buildJsonObject {
        put("type", type)
        put("title", title)
        put("status", status)
        detail?.let { put("detail", it) }
        instance?.let { put("instance", it) }
        errors?.let { list ->
            putJsonArray("errors") {
                list.forEach { e ->
                    add(
                        buildJsonObject {
                            e.field?.let { put("field", it) }
                            put("message", e.message)
                        },
                    )
                }
            }
        }
    }

/** The same Problem with a small hand-written helper. A real app would use its framework's serializer instead. */
private fun Problem<ErrorDetail>.toManualJson(): String {
    fun String.q() = "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    val members = mutableListOf("\"type\":${type.q()}", "\"title\":${title.q()}", "\"status\":$status")
    detail?.let { members += "\"detail\":${it.q()}" }
    instance?.let { members += "\"instance\":${it.q()}" }
    errors?.let { list ->
        val items =
            list.joinToString(",") { e ->
                listOfNotNull(e.field?.let { "\"field\":${it.q()}" }, "\"message\":${e.message.q()}").joinToString(",", "{", "}")
            }
        members += "\"errors\":[$items]"
    }
    return members.joinToString(",", "{", "}")
}

fun showProblemDetails(tasks: TaskService) {
    section("Part 5: Problem details (RFC 9457)")

    val problems = ProblemConverter()

    // <example id="rfc9457-minimal" tags="rfc9457,conversion">
    // A built-in status as an RFC 9457 problem. Built-in codes point at the kiit taxonomy page.
    val minimal = problems.convert(Rejected.CONFLICT)
    println(minimal.type) // https://www.kiit.dev/docs/kiit-codes?code=Failed:Rejected:CONFLICT#taxonomy
    println(minimal.title) // The request conflicts with the current state.
    println(minimal.status) // 409
    // </example>
    verify("rfc9457-minimal: type", minimal.type == "https://www.kiit.dev/docs/kiit-codes?code=Failed:Rejected:CONFLICT#taxonomy")
    verify("rfc9457-minimal: title", minimal.title == Rejected.CONFLICT.message)
    verify("rfc9457-minimal: status", minimal.status == 409)

    // The problem below is serialized three ways in the next examples
    val validation =
        Err.ErrorList(
            errors = listOf(Err.on("title", "", "must be 1-100 characters"), Err.on("listId", "x", "unknown list")),
            message = "Validation failed",
        )
    val problem = problems.convert(TaskCodes.EMPTY_TITLE, validation)

    // <example id="rfc9457-json-jackson" tags="rfc9457,json">
    // With Jackson (jackson-module-kotlin), the data class serializes directly
    val jacksonJson = problem.toJson()
    println(jacksonJson)
    // {
    //   "type" : "https://samples.kiit.dev/problems/invalid/empty-title",
    //   "title" : "Title must not be empty",
    //   "status" : 400,
    //   "detail" : "Validation failed",
    //   "errors" : [ {
    //     "field" : "title",
    //     "message" : "must be 1-100 characters"
    //   }, {
    //     "field" : "listId",
    //     "message" : "unknown list"
    //   } ]
    // }
    // </example>
    verify("rfc9457-json-jackson: type", problem.type == "https://samples.kiit.dev/problems/invalid/empty-title")
    verify("rfc9457-json-jackson: status", jackson.readTree(jacksonJson)["status"].asInt() == 400)
    verify("rfc9457-json-jackson: errors", jackson.readTree(jacksonJson)["errors"].size() == 2)

    // <example id="rfc9457-json-kotlinx" tags="rfc9457,json">
    // With kotlinx.serialization, kiit-codes classes aren't @Serializable so the mapping is written by hand
    val kotlinxJson = problem.toKotlinxJson().toString()
    // </example>
    verify("rfc9457-json-kotlinx: same JSON as Jackson", jackson.readTree(kotlinxJson) == jackson.readTree(jacksonJson))

    // <example id="rfc9457-json-manual" tags="rfc9457,json">
    // With no library, a small helper. A real app uses its framework's serializer.
    val manualJson = problem.toManualJson()
    // </example>
    verify("rfc9457-json-manual: same JSON as Jackson", jackson.readTree(manualJson) == jackson.readTree(jacksonJson))

    // <example id="rfc9457-domain-origin" tags="rfc9457,origin">
    // A custom code whose origin is a domain needs no registration: https://{origin}/problems/{group}/{name}
    val domain = problems.convert(TaskCodes.EMPTY_TITLE)
    println(domain.type) // https://samples.kiit.dev/problems/invalid/empty-title
    // </example>
    verify("rfc9457-domain-origin: type", domain.type == "https://samples.kiit.dev/problems/invalid/empty-title")

    // <example id="rfc9457-registered" tags="rfc9457,origin">
    // Register a base URL when the docs live somewhere else. An entry wins over the origin.
    val registered = ProblemConverter(baseUrls = mapOf("samples.kiit.dev" to "https://docs.samples.kiit.dev/errors"))
    println(registered.convert(TaskCodes.EMPTY_TITLE).type) // https://docs.samples.kiit.dev/errors/invalid/empty-title
    // </example>
    verify(
        "rfc9457-registered: type",
        registered.convert(TaskCodes.EMPTY_TITLE).type == "https://docs.samples.kiit.dev/errors/invalid/empty-title",
    )

    // <example id="rfc9457-scope" tags="rfc9457">
    // A scope becomes the first path segment
    val scoped = problems.convert(TaskCodes.DUPLICATE_TASK)
    println(scoped.type) // https://samples.kiit.dev/problems/lists.team/rejected/duplicate-task
    // </example>
    verify("rfc9457-scope: type", scoped.type == "https://samples.kiit.dev/problems/lists.team/rejected/duplicate-task")

    // <example id="rfc9457-errors" tags="rfc9457,usage">
    // An Err.ErrorList fills detail and errors (errors is a kiit extension member, not part of the RFC)
    val withErrors = problems.convert(Invalid.INVALID_VALUE, validation)
    println("${withErrors.detail}, ${withErrors.errors?.size} errors") // Validation failed, 2 errors
    // </example>
    verify("rfc9457-errors: detail", withErrors.detail == "Validation failed")
    verify("rfc9457-errors: errors", withErrors.errors?.size == 2)

    // <example id="rfc9457-custom-item" tags="rfc9457">
    // When field + message isn't enough, map each error into your own type
    data class RichError(override val field: String?, override val message: String, val hint: String) : ErrorItem

    val rich =
        problems.convertCustom<RichError>(Restricted.FORBIDDEN, validation) { err ->
            RichError((err as? Err.ErrorField)?.field, err.message, "ask the list owner instead")
        }
    println(rich.errors?.first()) // RichError(field=title, message=must be 1-100 characters, hint=ask the list owner instead)
    // </example>
    verify("rfc9457-custom-item: hint", rich.errors?.first()?.hint == "ask the list owner instead")
    verify("rfc9457-custom-item: count", rich.errors?.size == 2)

    // <example id="rfc9457-vs-codedetail" tags="rfc9457,conversion">
    // The same status as an RFC 9457 problem (for an HTTP API) and as kiit's own CodeDetail (service to service)
    val forbidden = tasks.complete("groceries", TaskService.TEAM_LIST, "amy") // Restricted.FORBIDDEN
    val asProblem = problems.convert(forbidden)
    val asDetail = toCodeDetail(forbidden, mapping = CodesToHttp())
    println(asProblem.toJson())
    println(asDetail.toJson())
    // Problem:    { "type" : "https://www.kiit.dev/docs/kiit-codes?code=Failed:Restricted:FORBIDDEN#taxonomy",
    //               "title" : "Access to this resource is forbidden.", "status" : 403 }
    // CodeDetail: { "path" : "kiit.dev", "code" : "Failed:Restricted:FORBIDDEN", "success" : false,
    //               "message" : "Access to this resource is forbidden.", "status" : 403 }
    // </example>
    verify("rfc9457-vs-codedetail: same status", asProblem.status == asDetail.status && asDetail.status == 403)
    verify("rfc9457-vs-codedetail: code", asDetail.code == forbidden.code)
    verify("rfc9457-vs-codedetail: path", asDetail.path == forbidden.path)

    // <example id="rfc9457-plain-origin" tags="rfc9457,origin">
    // A plain id is used as is. It looks like a host, so register a base URL or use a domain.
    val plain = Rejected(name = "OUT_OF_STOCK", message = "Out of stock", origin = "myapp1")
    println(problems.convert(plain).type) // https://myapp1/problems/rejected/out-of-stock
    val fixed = ProblemConverter(baseUrls = mapOf("myapp1" to "https://docs.example.com/myapp1/errors"))
    println(fixed.convert(plain).type) // https://docs.example.com/myapp1/errors/rejected/out-of-stock
    // </example>
    verify("rfc9457-plain-origin: as is", problems.convert(plain).type == "https://myapp1/problems/rejected/out-of-stock")
    verify(
        "rfc9457-plain-origin: registered",
        fixed.convert(plain).type == "https://docs.example.com/myapp1/errors/rejected/out-of-stock",
    )
}

fun main() {
    val tasks = TaskService()
    val validator = TaskValidator()
    showOverview(tasks)
    showTaxonomy(tasks)
    showUsage(tasks, validator)
    showProtocols()
    showProblemDetails(tasks)

    println()
    println("All $checks checks passed.")
}
