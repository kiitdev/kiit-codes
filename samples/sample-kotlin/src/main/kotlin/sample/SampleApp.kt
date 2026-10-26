package sample

// <example id="setup-imports" tags="setup">
import kiit.codes.*
import kiit.codes.formats.*
// </example>
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Canonical sample for kiit-codes. It holds every code block the docs show: the Tutorial steps and the Guide recipes.
 *
 * 1. Every example is wrapped in `// <example id="..." tags="...">` ... `// </example>` so the docs can show it with
 *    `<Example id="..." />`. The `verify(...)` calls sit outside the markers, they check the example still works.
 * 2. The other samples (Java, TypeScript, Swift) use the same ids, so one `<Example>` shows a tab per language.
 * 3. Run it with `./gradlew :samples:sample-kotlin:run`.
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

fun tester() {
    val MISSING_DATE = Invalid(
        name = "MISSING_DATE",
        title = "Date not supplied",
        origin = "samples.kiit.dev",
        scope = "tasks",
    )
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
// Tutorial: three short steps, each one a quick win
// ============================================================

fun showTutorialStatus() {
    section("Tutorial 1: Return a status")

    // <example id="tutorial-status" tags="tutorial">
    // A failure you expect is a value, not an exception
    fun validate(title: String): Status {
        return if (title.isBlank()) {
            Invalid.INVALID_VALUE
        } else {
            Succeeded.SUCCESS
        }
    }

    val good = validate("buy milk")
    // success = true, group = Succeeded, name = SUCCESS
    println("success = ${good.success}, group = ${good.group}, name = ${good.name}")

    val bad = validate("")
    // success = false, group = Invalid, name = INVALID_VALUE
    println("success = ${bad.success}, group = ${bad.group}, name = ${bad.name}")
    // </example>
    verify("tutorial-status: passes", good == Succeeded.SUCCESS && good.success && good.group == "Succeeded")
    verify("tutorial-status: fails", bad == Invalid.INVALID_VALUE && !bad.success && bad.group == "Invalid")
}

fun showTutorialValidate() {
    section("Tutorial 2 and 3: Validate, then build a problem")

    // <example id="tutorial-validate" tags="tutorial">
    // Checked carries the status and the errors together
    fun validate(title: String): Checked =
        if (title.isNotBlank()) {
            Checked.success()
        } else {
            Checked.failure(
                Invalid.INVALID_VALUE,
                listOf(
                    Err.on("title", title, "must not be blank"),
                ),
            )
        }

    val checked = validate("")
    // failed: INVALID_VALUE
    when (val status = checked.status) {
        is Passed -> println("ok: ${status.name}")
        is Failed -> println("failed: ${status.name}")
    }
    // </example>
    verify("tutorial-validate: failed", !checked.isValid && checked.status is Invalid)
    verify("tutorial-validate: one error", checked.errors.size == 1)

    // <example id="tutorial-problem" tags="tutorial,rfc9457">
    val errors =
        Err.ErrorList(
            errors = checked.errors,
            message = "Validation failed",
        )

    // The failure as an RFC 9457 problem, for an HTTP API
    val problem = ProblemConverter().convert(checked.status, errors)
    // 400
    println(problem.status)
    // 1
    println(problem.errors?.size)

    // The same failure as kiit's CodeDetail, self-contained, for your own services
    val detail = toCodeDetail(checked.status, errors)
    // kiit.dev:codes:Failed:Invalid:INVALID_VALUE
    println(detail.code)
    // false
    println(detail.success)
    // </example>
    verify("tutorial-problem: type", problem.type == "https://www.kiit.dev/docs/kiit-codes?code=Failed:Invalid:INVALID_VALUE#codes")
    verify("tutorial-problem: title", problem.title == "The request had an invalid value.")
    verify("tutorial-problem: status", problem.status == 400)
    verify("tutorial-problem: detail", problem.detail == "Validation failed")
    verify("tutorial-problem: errors", problem.errors?.map { it.field to it.message } == listOf("title" to "must not be blank"))
    verify("tutorial-problem: code", problem.code == "kiit.dev:codes:Failed:Invalid:INVALID_VALUE")
    verify("tutorial-problem: detail code", detail.code == "kiit.dev:codes:Failed:Invalid:INVALID_VALUE")
    verify("tutorial-problem: detail success", !detail.success)
    verify("tutorial-problem: detail title", detail.title == "The request had an invalid value.")
    verify("tutorial-problem: detail text", detail.detail == "Validation failed")
    verify("tutorial-problem: detail errors", detail.errors?.map { it.field to it.message } == listOf("title" to "must not be blank"))
}

// ============================================================
// Guide: recipes. Each one stands alone, so the page can show it as it is.
// ============================================================

fun showGuide() {
    guideBuiltins()
    guideCustomCode()
    guidePatternMatching()
    guideCollectErrors()
    guideErrorDetails()
    guideExceptions()
    guideExceptionsSubclass()
    guideJson()
    guideTypeUrl()
    guideCustomTypeUrl()
    guideHttpGrpc()
    guideCustomProtocol()
}

private fun guideBuiltins() {
    section("Guide: Status: Built-ins")

    // <example id="guide-builtins" tags="guide">
    // A specific built-in code
    val created = Succeeded.CREATED
    // The group's default, when you only know the kind of outcome
    val failed = Invalid.DEFAULT
    // INVALID_VALUE
    println(failed.name)
    // true
    println(failed.isDefault)
    // false
    println(Invalid.BAD_REQUEST.isDefault)
    // </example>
    verify("guide-builtins: created", created.name == "CREATED")
    verify("guide-builtins: default", failed == Invalid.INVALID_VALUE && failed.isDefault)
    verify("guide-builtins: not default", !Invalid.BAD_REQUEST.isDefault)
}

private fun guideCustomCode() {
    section("Guide: Status: Custom code")

    // <example id="guide-custom-code" tags="guide">
    val PAYMENT_DECLINED =
        Rejected(
            name = "PAYMENT_DECLINED",
            title = "Payment declined",
            origin = "payments.example.com",
            scope = "payments.cards",
        )

    // A bad origin, scope or name throws when the status is created. For values from config, check them first
    // null, the values are valid
    println(StatusRules.check(origin = "payments.example.com", scope = "payments.cards", name = "PAYMENT_DECLINED"))
    // origin "host:8080" is invalid: allowed characters are a-z, A-Z, 0-9, '.', '-', '_'
    println(StatusRules.check(origin = "host:8080", scope = "", name = "PAYMENT_DECLINED"))
    // </example>
    verify("guide-custom-code: check valid", StatusRules.check("payments.example.com", "payments.cards", "PAYMENT_DECLINED") == null)
    verify("guide-custom-code: check invalid", StatusRules.check("host:8080", "", "PAYMENT_DECLINED") == "origin \"host:8080\" is invalid: allowed characters are a-z, A-Z, 0-9, '.', '-', '_'")
    verify("guide-custom-code: group", PAYMENT_DECLINED.group == "Rejected")
    verify("guide-custom-code: origin and scope", PAYMENT_DECLINED.origin == "payments.example.com" && PAYMENT_DECLINED.scope == "payments.cards")
}

private fun guidePatternMatching() {
    section("Guide: Status: Pattern matching")

    // <example id="guide-pattern-matching" tags="guide">
    // 1. Passed or Failed
    fun binary(status: Status): String =
        when (status) {
            is Passed -> "ok: ${status.name}"
            is Failed -> "failed: ${status.name}"
        }

    // 2. Passed or Failed, each by its four groups. The compiler flags a missing group.
    fun nested(status: Status): String =
        when (status) {
            is Passed ->
                when (status) {
                    is Succeeded -> "done"
                    is Pending -> "in progress"
                    is Excluded -> "skipped"
                    is Information -> "for your information"
                }
            is Failed ->
                when (status) {
                    is Restricted -> "not allowed"
                    is Invalid -> "fix the input"
                    is Rejected -> "refused by a rule"
                    is Unserved -> "try again later"
                }
        }

    // 3. Specific to broad: a code, then a group, then Failed or Passed
    fun hybrid(status: Status): String =
        when (status) {
            Rejected.CONFLICT -> "already exists"
            is Invalid -> "fix the input"
            is Failed -> "failed: ${status.name}"
            is Passed -> "ok: ${status.name}"
        }

    // failed: INVALID_VALUE
    println(binary(Invalid.INVALID_VALUE))
    // in progress
    println(nested(Pending.QUEUED))
    // already exists
    println(hybrid(Rejected.CONFLICT))
    // </example>
    verify("guide-pattern-matching: binary", binary(Invalid.INVALID_VALUE) == "failed: INVALID_VALUE" && binary(Succeeded.SUCCESS) == "ok: SUCCESS")
    verify("guide-pattern-matching: nested passed", nested(Succeeded.SUCCESS) == "done" && nested(Pending.QUEUED) == "in progress")
    verify("guide-pattern-matching: nested passed rest", nested(Excluded.SKIPPED) == "skipped" && nested(Information.NOTICE) == "for your information")
    verify("guide-pattern-matching: nested failed", nested(Invalid.INVALID_VALUE) == "fix the input" && nested(Unserved.TIMEOUT) == "try again later")
    verify("guide-pattern-matching: nested failed rest", nested(Restricted.DENIED) == "not allowed" && nested(Rejected.CONFLICT) == "refused by a rule")
    verify("guide-pattern-matching: hybrid code first", hybrid(Rejected.CONFLICT) == "already exists")
    verify("guide-pattern-matching: hybrid group", hybrid(Invalid.INVALID_VALUE) == "fix the input")
    verify("guide-pattern-matching: hybrid broad", hybrid(Unserved.TIMEOUT) == "failed: TIMEOUT" && hybrid(Succeeded.SUCCESS) == "ok: SUCCESS")
}

private fun guideCollectErrors() {
    section("Guide: Error Handling: Collect errors")

    // <example id="guide-collect-errors" tags="guide">
    fun validateTitle(title: String): Checked =
        if (title.isNotBlank()) {
            Checked.success()
        } else {
            Checked.failure(Invalid.INVALID_VALUE, listOf(Err.on("title", title, "must not be blank")))
        }

    fun validateListId(listId: String): Checked =
        if (listId in setOf("personal", "team")) {
            Checked.success()
        } else {
            Checked.failure(Invalid.NOT_FOUND, listOf(Err.on("listId", listId, "unknown list")))
        }

    val checked =
        collect(
            validateTitle(""),
            validateListId("unknown-list"),
        )
    // valid = false, errors = 2
    println("valid = ${checked.isValid}, errors = ${checked.errors.size}")
    // </example>
    verify("guide-collect-errors: invalid", !checked.isValid)
    verify("guide-collect-errors: two errors", checked.errors.size == 2)
}

private fun guideErrorDetails() {
    section("Guide: Error Handling: Error details")

    // <example id="guide-error-details" tags="guide">
    // A plain message
    val plain = Err.of("title is required")
    // An error on one field, with its value
    val title = Err.on("title", "", "must be 1-100 characters")
    // The same, without the value
    val password = Err.on("password", "must be at least 12 characters")
    // Several plain messages under one message
    val many = Err.list(listOf("title is required", "list is unknown"), "Validation failed")
    // </example>
    verify("guide-error-details: plain", plain.message == "title is required")
    verify("guide-error-details: field", title is Err.ErrorField && title.field == "title")
    verify("guide-error-details: no value", password is Err.ErrorField && password.field == "password")
    verify("guide-error-details: list", many.errors.size == 2 && many.message == "Validation failed")
}

private fun guideExceptions() {
    section("Guide: Error Handling: Exceptions")

    // <example id="guide-exceptions" tags="guide">
    fun create(title: String): Status =
        if (title == "groceries") Rejected.CONFLICT else Succeeded.CREATED

    fun createOrThrow(title: String) {
        val status = create(title)
        if (status is Failed) throw status.toException()
    }

    try {
        createOrThrow("groceries")
    } catch (e: StatusException.RejectedException) {
        // CONFLICT
        println(e.status.name)
    }
    // </example>
    val caught =
        try {
            createOrThrow("groceries")
            null
        } catch (e: StatusException.RejectedException) {
            e.status.name
        }
    verify("guide-exceptions: caught", caught == "CONFLICT")
}

private fun guideExceptionsSubclass() {
    section("Guide: Error Handling: Exceptions: Subclass")

    // <example id="guide-exceptions-subclass" tags="guide">
    // A domain exception: the status is fixed, the data and the message are yours
    class OrderShippedException(val orderId: String) :
        StatusException.RejectedException(Rejected.CONFLICT, message = "Order $orderId already shipped")

    fun describe(e: StatusException): String =
        when (e) {
            // The narrow type goes first, the first match wins
            is OrderShippedException -> "order ${e.orderId} shipped"
            is StatusException.RejectedException -> "rejected"
            is StatusException.RestrictedException -> "restricted"
            is StatusException.InvalidException -> "invalid"
            is StatusException.UnservedException -> "unserved"
        }

    // order A-1 shipped
    println(describe(OrderShippedException("A-1")))
    // rejected
    println(describe(Rejected.CONFLICT.toException()))

    try {
        throw OrderShippedException("A-1")
    } catch (e: OrderShippedException) {
        // Order A-1 already shipped
        println(e.message)
    } catch (e: StatusException.RejectedException) {
        println(e.status.name)
    }
    // </example>
    verify("guide-exceptions-subclass: narrow first", describe(OrderShippedException("A-1")) == "order A-1 shipped")
    verify("guide-exceptions-subclass: group", describe(Rejected.CONFLICT.toException()) == "rejected")
    verify("guide-exceptions-subclass: message", OrderShippedException("A-1").message == "Order A-1 already shipped")
}

private fun guideJson() {
    section("Guide: Response: JSON")

    // <example id="guide-json" tags="guide">
    @Serializable
    data class ErrorJson(val field: String? = null, val message: String)

    @Serializable
    data class ProblemJson(
        val type: String,
        val title: String,
        val detail: String? = null,
        val code: String? = null,
        val status: Int? = null,
        val errors: List<ErrorJson>? = null,
    )

    @Serializable
    data class CodeDetailJson(
        val code: String,
        val title: String,
        val detail: String? = null,
        val success: Boolean,
        val errors: List<ErrorJson>? = null,
    )

    fun Problem<ErrorDetail>.toJson() =
        ProblemJson(type, title, detail, code, status, errors?.map { ErrorJson(it.field, it.message) })

    fun CodeDetail<ErrorDetail>.toJson() =
        CodeDetailJson(code, title, detail, success, errors?.map { ErrorJson(it.field, it.message) })

    val json = Json { prettyPrint = true }
    val errors = Err.ErrorList(listOf(Err.on("title", "", "must be 1-100 characters")), "Validation failed")

    // An RFC 9457 problem, for an HTTP API
    println(json.encodeToString(ProblemConverter().convert(Invalid.INVALID_VALUE, errors).toJson()))
    // A CodeDetail, for your own services
    println(json.encodeToString(toCodeDetail(Invalid.INVALID_VALUE, errors).toJson()))
    // </example>
    val problemText = json.encodeToString(ProblemConverter().convert(Invalid.INVALID_VALUE, errors).toJson())
    val codeText = json.encodeToString(toCodeDetail(Invalid.INVALID_VALUE, errors).toJson())
    verify("guide-json: problem field order", problemText.lines().map { it.trim().substringBefore(":") }.take(7) == listOf("{", "\"type\"", "\"title\"", "\"detail\"", "\"code\"", "\"status\"", "\"errors\""))
    verify("guide-json: code detail field order", codeText.lines().map { it.trim().substringBefore(":") }.take(6) == listOf("{", "\"code\"", "\"title\"", "\"detail\"", "\"success\"", "\"errors\""))
}

private fun guideTypeUrl() {
    section("Guide: Response: Type URL")

    // <example id="guide-type-url" tags="guide">
    val stripe = Rejected("DUPLICATE_CHARGE", "Duplicate charge", origin = "stripe.com", scope = "payments.cards")
    val plain = Rejected("OUT_OF_STOCK", "Out of stock", origin = "myapp1")

    // 1. A domain origin and nothing registered
    // https://stripe.com/docs/codes/payments/cards/Failed/Rejected/DUPLICATE_CHARGE
    println(ProblemConverter().convert(stripe).type)

    // 2. A plain id is not a domain, so the type is relative
    // /docs/codes/Failed/Rejected/OUT_OF_STOCK
    println(ProblemConverter().convert(plain).type)

    // 3. A base URL registered for the origin
    // https://stripe.com/errors/payments/cards/Failed/Rejected/DUPLICATE_CHARGE
    val registered = ProblemConverter(baseUrls = mapOf("stripe.com" to "https://stripe.com/errors"))
    println(registered.convert(stripe).type)
    // </example>
    verify("guide-type-url: domain", ProblemConverter().convert(stripe).type == "https://stripe.com/docs/codes/payments/cards/Failed/Rejected/DUPLICATE_CHARGE")
    verify("guide-type-url: plain id", ProblemConverter().convert(plain).type == "/docs/codes/Failed/Rejected/OUT_OF_STOCK")
    verify("guide-type-url: registered", registered.convert(stripe).type == "https://stripe.com/errors/payments/cards/Failed/Rejected/DUPLICATE_CHARGE")
}

private fun guideCustomTypeUrl() {
    section("Guide: Response: Custom type URL")

    // <example id="guide-custom-type-url" tags="guide">
    val stripe = Rejected("DUPLICATE_CHARGE", "Duplicate charge", origin = "stripe.com", scope = "payments.cards")
    val problems = ProblemConverter()

    // 1. Your own suffix, with a type builder
    // https://stripe.com/docs/codes/charges/duplicate
    println(problems.convert(stripe, typeBuilder = { "charges/duplicate" }).type)

    // 2. Exactly one URL, a base with an empty suffix
    // https://example.com/probs/duplicate-charge
    println(
        problems.convertWithUrl(
            stripe,
            baseUrl = "https://example.com/probs/duplicate-charge",
            typeBuilder = { "" },
        ).type,
    )

    // 3. Any URL at all, by copying the Problem
    // https://other.example.org/probs/duplicate
    println(problems.convert(stripe).copy(type = "https://other.example.org/probs/duplicate").type)
    // </example>
    verify("guide-custom-type-url: builder", problems.convert(stripe, typeBuilder = { "charges/duplicate" }).type == "https://stripe.com/docs/codes/charges/duplicate")
    verify(
        "guide-custom-type-url: exact",
        problems.convertWithUrl(stripe, baseUrl = "https://example.com/probs/duplicate-charge", typeBuilder = { "" }).type ==
            "https://example.com/probs/duplicate-charge",
    )
    verify("guide-custom-type-url: copy", problems.convert(stripe).copy(type = "https://other.example.org/probs/duplicate").type == "https://other.example.org/probs/duplicate")
    verify("guide-custom-type-url: code unchanged", problems.convert(stripe).code == problems.convert(stripe).copy(type = "https://x.example/y").code)
}

private fun guideHttpGrpc() {
    section("Guide: Response: HTTP and gRPC")

    // <example id="guide-http" tags="guide">
    val http = CodesToHttp()
    val grpc = CodesToGrpc()

    // 201
    println(http.toCode(Succeeded.CREATED))
    // 409
    println(http.toCode(Rejected.CONFLICT))

    // 0 (OK)
    println(grpc.toCode(Succeeded.CREATED))
    // 6 (ALREADY_EXISTS)
    println(grpc.toCode(Rejected.CONFLICT))
    // </example>
    verify("guide-http: http", http.toCode(Succeeded.CREATED) == 201 && http.toCode(Rejected.CONFLICT) == 409)
    verify("guide-http: grpc", grpc.toCode(Succeeded.CREATED) == 0 && grpc.toCode(Rejected.CONFLICT) == 6)
}

private fun guideCustomProtocol() {
    section("Guide: Response: Custom protocol")

    // <example id="guide-custom-protocol" tags="guide">
    val PAYMENT_DECLINED = Rejected("PAYMENT_DECLINED", "Payment declined", origin = "payments.example.com")

    val http =
        CompositeLookup(
            base = CodesToHttp(),
            extensions = mapOf(PAYMENT_DECLINED to 402),
        )
    // 402
    println(http.toCode(PAYMENT_DECLINED))
    // 409
    println(http.toCode(Rejected.CONFLICT))
    // </example>
    verify("guide-custom-protocol: extension", http.toCode(PAYMENT_DECLINED) == 402)
    verify("guide-custom-protocol: falls back to the base", http.toCode(Rejected.CONFLICT) == 409)
}

// ============================================================
// Formats: checked here, not shown on the docs site
// ============================================================

/** When field + message isn't enough, map each error into your own type with `convertCustom`. */
fun showCustomErrorItem() {
    section("Formats: Custom error item")

    data class RichError(
        override val field: String?,
        override val message: String,
        val hint: String,
    ) : ErrorItem

    val validation = Err.ErrorList(
        listOf(
            Err.on("title", "", "must be 1-100 characters"),
            Err.on("listId", "x", "unknown list"),
        ),
        "Validation failed",
    )
    val problem = ProblemConverter().convertCustom(Restricted.FORBIDDEN, validation) { err ->
        RichError((err as? Err.ErrorField)?.field, err.message, "ask the list owner instead")
    }

    // RichError(field=title, message=must be 1-100 characters, hint=ask the list owner instead)
    println(problem.errors?.first())
    verify("convertCustom: hint", problem.errors?.first()?.hint == "ask the list owner instead")
    verify("convertCustom: field", problem.errors?.first()?.field == "title")
    verify("convertCustom: count", problem.errors?.size == 2)
    verify("convertCustom: status", problem.status == 403)
}

fun main() {
    showTutorialStatus()
    showTutorialValidate()
    showGuide()
    showCustomErrorItem()

    println()
    println("All $checks checks passed.")
}
