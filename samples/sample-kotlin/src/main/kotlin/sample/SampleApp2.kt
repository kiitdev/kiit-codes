package sample

import kiit.codes.*
import kiit.codes.formats.*

/**
 * Scratchpad draft — not wired into the build yet, for review only.
 *
 * One running example (TaskService, a to-do list) threaded through four parts: Overview,
 * Taxonomy, Usage, Conversion. Self-contained on purpose (doesn't touch
 * UserService.kt/SampleApp.kt) so it can be reviewed and reshaped freely before anything
 * replaces the originals.
 */

// ============================================================
// Domain: a minimal to-do list — the one running example used across every part below.
// ============================================================

data class Task(val title: String, val listId: String)

class TaskService {
    companion object {
        const val PERSONAL_LIST = "personal"
        const val TEAM_LIST = "team"
    }

    private val listOwners = mapOf(PERSONAL_LIST to "amy", TEAM_LIST to "erin")
    private val existingTitles = mutableSetOf("groceries")

    /** The spine: three built-in outcomes plus one custom, domain-specific code. */
    fun create(title: String, listId: String = PERSONAL_LIST): Status {
        if (title.isBlank()) {
            return Invalid(name = "EMPTY_TITLE", message = "Title must not be empty", origin = "dev.kiit.samples")
        }
        if (title in existingTitles) {
            return Rejected.CONFLICT
        }
        existingTitles += title
        return Succeeded.CREATED
    }


    /** The spine: three built-in outcomes plus one custom, domain-specific code. */
    fun exists(title: String, listId: String = PERSONAL_LIST): Boolean {
        return existingTitles.contains(title)
    }

    /** A second operation on the same domain — reaches Rejected.NOT_EXISTS and a custom Restricted. */
    fun complete(title: String, listId: String, requesterId: String): Status {
        if (title !in existingTitles) {
            return Rejected.NOT_EXISTS
        }
        if (listOwners[listId] != requesterId) {
            return Restricted(name = "NOT_LIST_OWNER", message = "Only the list owner can complete this task", origin = "dev.kiit.samples")
        }
        return Succeeded.SUCCESS
    }

    /** A boundary that can only communicate via exceptions — wraps create's Failed outcome. */
    fun createTaskOrThrow(title: String, listId: String = PERSONAL_LIST): Task {
        val status = create(title, listId)
        if (status is Failed) throw status.toException()
        return Task(title, listId)
    }
}

/** Two independent field checks, kept separate so Part 3 can combine them with collect(). */
class TaskValidator {
    fun validateTitle(title: String): Checked =
        if (title.isNotBlank() && title.length <= 100) Checked.success()
        else Checked.failure(Invalid.BAD_REQUEST, listOf(Err.on("title", title, "must be 1-100 characters")))

    fun validateListId(listId: String): Checked =
        if (listId in knownLists) Checked.success()
        else Checked.failure(Invalid.NOT_FOUND, listOf(Err.on("listId", listId, "unknown list")))

    companion object {
        private val knownLists = setOf(TaskService.PERSONAL_LIST, TaskService.TEAM_LIST)
    }
}

// ============================================================
// Shared helpers
// ============================================================

private fun section(title: String) {
    val bar = "=".repeat(60)
    println()
    println(bar)
    println(title)
    println(bar)
}

/** Exhaustive over all 8 groups — compiler-enforced, no `else` branch. */
fun describe(status: Status): String =
    when (status) {
        is Succeeded -> "Succeeded: ${status.name}"
        is Pending -> "Pending: ${status.name}"
        is Excluded -> "Excluded: ${status.name}"
        is Information -> "Information: ${status.name}"
        is Restricted -> "Restricted: ${status.name}"
        is Invalid -> "Invalid: ${status.name}"
        is Rejected -> "Rejected: ${status.name}"
        is Unserved -> "Unserved: ${status.name}"
    }

fun printDetail(label:String, status: Status){
    println(label +
        "name    =${status.name}    " +
        "group   =${status.group}   " +
        "origin  =${status.origin}  " +
        "scope   =${status.scope}   " +
        "success =${status.success} " +
        "message =${status.message} " ,
    )
}

// ============================================================
// Part 1: Overview — usage, defaults, construction, shape
// ============================================================

fun showOverview(tasks: TaskService) {
    section("Part 1: Overview")

    // Overview: Status codes
    // 1. These are Like http codes, but generalized for any layer.
    // 2. Tell you the KIND of success/failure ( e.g. security = Failed.Restricted)
    // 3. Error details are separate from the Status codes ( example shown later )
    //
    // Status  = Passed     | Failed
    // Passed  = Succeeded  | Pending  | Excluded  | Information
    // Failed  = Restricted | Invalid  | Rejected  | Unserved

    // Example 1: Usage of Status Codes
    // Let's start with a small example of using a few ( create one, and use 2 defaults )
    val title = "Get groceries"
    val validated: Status = when {
        title.isEmpty()     -> Invalid(name = "EMPTY_TITLE", message = "Title required", origin = "dev.kiit.samples")
        tasks.exists(title) -> Rejected.CONFLICT
        else                -> Succeeded.SUCCESS
    }

    // Example 2: Shape of each Status code
    // These are the fields every Status carries, whether built-in or custom.
    // origin = where the status code came from
    // scope  = organizational label by department/product area etc.
    // {
    //      name = "SUCCESS",
    //      group = "Succeeded",
    //      origin = "dev.kiit",
    //      scope  = "",
    //      success = true,
    //      message = "The operation completed successfully.",
    // }
    println(
        "shape: name=${validated.name} group=${validated.group} origin=${validated.origin} " +
            "scope=${validated.scope} message=${validated.message} success=${validated.success}",
    )

    // Example 3: Checks
    // You can use .success ( simpler ), or the Status branches ( Tier 1 = Passed | Failed )
    if(validated.success) {
        println("usage: .success -> ${validated.name}")
    }
    when (validated) {
        is Passed -> println("usage: Passed -> ${validated.name}")
        is Failed -> println("usage: Failed -> ${validated.name}: ${validated.message}")
    }
}

// ============================================================
// Part 2: Taxonomy — status/passed/failed, groups, default codes, custom codes
// ============================================================

fun showTaxonomy(tasks: TaskService) {
    section("Part 2: Taxonomy")

    // Example 1: There are 8 groups total under the Passed/Failed Branches ( Tier 1 )
    // Each group is Tier 2, representing tier of information ( Kind
    // Passed  = Succeeded  | Pending | Excluded | Information
    // Failed  = Restricted | Invalid | Rejected | Unserved
    val created: Status = tasks.create("walk the dog")
    when(created) {
        // Tier 1: Passed / Failed
        is Passed -> {
            // Tier 2: Passed has 4 groups
            when(created) {
                is Passed.Succeeded   -> println("Operation is succeeful")
                is Passed.Pending     -> println("Operation is pending")
                is Passed.Excluded    -> println("Operation is excluded")
                is Passed.Information -> println("Operation is informational")
            }
        }
        is Failed -> {
            // Tier 2: Failed also has 4 groups
            when(created) {
                is Failed.Restricted  -> println("Operation is restricted")
                is Failed.Invalid     -> println("Operation is invalid")
                is Failed.Rejected    -> println("Operation is rejected")
                is Failed.Unserved    -> println("Operation is unserved")
            }
        }
    }

    // Example 2: Defaults codes are available for convenience
    // There all defaults for all 8 groups ( 4 in Passed, 4 in Failed )
    println(Succeeded.SUCCESS)
    println(Pending.ACCEPTED)
    println(Excluded.OMITTED)
    println(Information.NOTICE)
    println(Restricted.DENIED)
    println(Invalid.INVALID_VALUE)
    println(Rejected.RULE_VIOLATION)
    println(Unserved.UNEXPECTED)
    val title = "Get groceries"
    val checkWithDefaultCodes: Status = when {
        title.isEmpty()     -> Invalid.INVALID_VALUE
        tasks.exists(title) -> Rejected.CONFLICT
        else                -> Succeeded.SUCCESS
    }
    printDetail("use defaults", checkWithDefaultCodes)


    // Example 3: Custom codes
    // Create your own status codes from the 8 groups available ( here is a sample of 3 )
    // NOTE: This are stateless / constant, they just tell you the Kind of error ( Invalid | Rejected | Succeeded )
    val itemInvalid = Invalid(name = "MISSING_DATE", message = "Date not supplied", origin = "dev.kiit.samples")
    val itemExists = Rejected(name = "DUPLICATE", message = "Item already exists", origin = "dev.kiit.samples")
    val itemValid = Succeeded(name = "TASK_VALID", message = "Item is valid", origin = "dev.kiit.samples")
    val checkWithCustomCodes: Status = when {
        title.isEmpty()     -> itemInvalid
        tasks.exists(title) -> itemExists
        else                -> itemValid
    }
    printDetail("use custom", checkWithCustomCodes)
}

// ============================================================
// Part 3: Usage — errors (kind vs detail), as a status, as a checked, as an exception
// ============================================================

fun showUsage(tasks: TaskService, validator: TaskValidator) {
    section("Part 3: Usage")

    // errors (kind vs detail) — Err's sealed variants (its "kind") vs. the simplified
    // ErrorDetail shape (formats.ErrorDetail) used later, at conversion time, when the full
    // Err isn't needed.
    val fieldErr = Err.on("title", "", "must be 1-100 characters")
    val kind =
        when (fieldErr) {
            is Err.ErrorInfo -> "ErrorInfo"
            is Err.ErrorField -> "ErrorField"
            is Err.ErrorList -> "ErrorList"
        }
    val detail = ErrorDetail(field = (fieldErr as? Err.ErrorField)?.field, message = fieldErr.message)
    println("errors: kind=$kind, detail=$detail")

    // as a status (on its own) — the plain, no-wrapper case.
    val status = tasks.create("buy milk")
    println("as a status: ${describe(status)}")

    // as a checked — combining two independent field checks with collect().
    val combined = collect(validator.validateTitle(""), validator.validateListId("unknown-list"))
    println("as a checked: isValid=${combined.isValid}, errors=${combined.errors.size}")

    // as an exception — a boundary that can only communicate via exceptions, catching a
    // specific subclass rather than the base StatusException.
    try {
        tasks.createTaskOrThrow("groceries") // already exists -> Rejected.CONFLICT
    } catch (e: StatusException.RejectedException) {
        println("as an exception: caught ${e.status.name} - ${e.message}")
    }
}

// ============================================================
// Part 4: Conversion — to an api status, protocols, problem detail (rfc 9457), customization
// ============================================================

fun showConversion(tasks: TaskService) {
    section("Part 4: Conversion")

    // Only the list owner can complete a task — a rich, non-security Restricted example.
    val status = tasks.complete("groceries", TaskService.TEAM_LIST, "amy")

    // to an api status — completeTask exposed as an HTTP endpoint.
    val http = CodesToHttp()
    println("to an api status: http=${http.toCode(status)}")

    // protocols — the same completeTask exposed as an internal gRPC call.
    val grpc = CodesToGrpc()
    println("protocols: grpc=${grpc.toCode(status)}")

    // problem detail (rfc 9457) — completeTask's HTTP API error response.
    val catalog = Catalog.of(mapOf("dev.kiit.samples" to "https://example.com/problems"))
    val problems = CodesToProblem(catalog, http)
    val problem = problems.build(status)
    println("problem detail: type=${problem.type}, status=${problem.status}")

    // customization — kiit's own native shape for a non-HTTP boundary (e.g. a background job)...
    val detail = toCodeDetail(status, mapping = http)
    println("customization (CodeDetail): path=${detail.path}, code=${detail.code}, status=${detail.status}")

    // ...and a custom ErrorItem when the default field+message ErrorDetail isn't enough.
    data class RichError(override val field: String?, override val message: String, val hint: String) : ErrorItem

    val fieldErr = Err.on("listId", TaskService.TEAM_LIST, "not owned by this requester")
    val richProblem =
        problems.buildCustom<RichError>(status, fieldErr) { err ->
            RichError((err as? Err.ErrorField)?.field, err.message, "ask the list owner to complete it instead")
        }
    println("customization (buildCustom): ${richProblem.errors}")
}

fun showMisc(tasks: TaskService) {
    // status/passed/failed — the two-branch split, using createTask/completeTask's own outcomes.
    val outcomes =
        listOf(
            tasks.create(""), // Failed: custom EMPTY_TITLE
            tasks.create("groceries"), // Failed: built-in Rejected.CONFLICT (already exists)
            tasks.create("read a book"), // Passed: built-in Succeeded.CREATED
            tasks.complete("missing item", TaskService.PERSONAL_LIST, "amy"), // Failed: built-in Rejected.NOT_EXISTS
            tasks.complete("groceries", TaskService.TEAM_LIST, "amy"), // Failed: custom Restricted.NOT_LIST_OWNER
        )
    outcomes.forEach { println("status/passed/failed: success=${it.success} -> ${describe(it)}") }


}

fun main() {
    val tasks = TaskService()
    val validator = TaskValidator()
    showOverview(tasks)
    showTaxonomy(tasks)
    showUsage(tasks, validator)
    showConversion(tasks)
}
