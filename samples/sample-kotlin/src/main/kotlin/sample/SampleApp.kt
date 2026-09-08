package sample

import kiit.codes.Checked
import kiit.codes.CodesToHttp
import kiit.codes.Err
import kiit.codes.Excluded
import kiit.codes.Failed
import kiit.codes.Information
import kiit.codes.Invalid
import kiit.codes.Passed
import kiit.codes.Pending
import kiit.codes.Rejected
import kiit.codes.Restricted
import kiit.codes.Status
import kiit.codes.StatusException
import kiit.codes.Succeeded
import kiit.codes.Unserved
import kiit.codes.code
import kiit.codes.path
import kiit.codes.toCodeDetail
import kiit.codes.toProblemDetail
import kotlin.random.Random

private val http = CodesToHttp()

fun main() {
    test0()
    test1()
    test2()
    test3()
    test4()
}

fun test1() {
    val service = UserService()

    report("create alice", service.create("alice", "alice@example.com"))
    report("create alice again", service.create("alice", "alice@example.com"))
    report("create with blank email", service.create("bob", ""))

    report("authorize alice as alice", service.authorize("alice", "alice"))
    report("authorize alice as bob", service.authorize("alice", "bob"))
    report("authorize unknown user", service.authorize("carol", "carol"))

    try {
        service.requireAuthorized("alice", "bob")
    } catch (e: StatusException) {
        println("caught StatusException: ${e.status.name} — ${e.message}")
    }
}

private fun report(label: String, status: Status) {
    val outcome =
        when (status) {
            is Passed -> "ok"
            is Failed -> "failed"
        }
    println("$label -> ${status.name} ($outcome, http=${http.toCode(status)})")
}

fun test0() {
    val status =
        Failed.Invalid(
            name = "CREATED",
            message = "failure",
            origin = "kiit"
        )

    println(status.success) // false
    println(CodesToHttp().toCode(status)) // 400
}

fun test2() {
    val value = Random.nextInt(1, 8)
    val result: Status = when (value) {
        1 -> Succeeded.SUCCESS
        2 -> Pending.ACCEPTED
        3 -> Excluded.SKIPPED
        4 -> Information.NOTICE
        5 -> Restricted.DENIED
        6 -> Invalid.INVALID_VALUE
        7 -> Rejected.RULE_VIOLATION
        else -> Unserved.UNEXPECTED
    }

    val name = when(result) {
        is Passed -> {
            when(result) {
                is Passed.Succeeded   -> "Succeeded"
                is Passed.Pending     -> "Pending"
                is Passed.Excluded    -> "Excluded"
                is Passed.Information -> "Information"
            }
        }
        is Failed -> {
            when(result) {
                is Failed.Restricted  -> "Restricted"
                is Failed.Invalid     -> "Invalid"
                is Failed.Rejected    -> "Rejected"
                is Failed.Unserved    -> "Unserved"
            }
        }
    }
    println("${name.padEnd(11)}: ${result.name}")
}


fun test3() {
    val check1 = validatePhone("")
    val check2 = validatePhone("12345678901")
    val check3 = validatePhone("1111111111")
    val check4 = validatePhone("1234567890")
    val check5 = validatePhone("9876543210")
    val check6 = validatePhone("1234567890", "admin")
    println(check1)
    println(check2)
    println(check3)
    println(check4)
    println(check5)
    println(check6)
}

fun test4() {
    // Custom, consumer-defined Status: a non-kiit origin plus an internal-organization scope,
    // distinct from kiit-codes' own built-in registry (see Codes.kt).
    val duplicateCharge =
        Rejected(
            name = "DUPLICATE_CHARGE",
            message = "This charge has already been processed",
            origin = "com.stripe",
            scope = "payments.cards",
        )
    println("path: ${duplicateCharge.path}") // com.stripe:payments.cards
    println("code: ${duplicateCharge.code}") // Failed:Rejected:DUPLICATE_CHARGE

    // Two independent converters off the same Status, pick whichever fits the boundary:

    // toProblemDetail: the RFC 9457 shape, for an HTTP API response. baseUrl is required here
    // since origin isn't kiit-codes' own ("dev.kiit").
    val stripeProblem = toProblemDetail(duplicateCharge, baseUrl = "https://stripe.com/problems")
    println("[rfc]  type: ${stripeProblem.type}")
    println("[rfc]  title: ${stripeProblem.title}")
    println("[rfc]  status: ${stripeProblem.status}")

    // toCodeDetail: kiit-codes' own shape, no baseUrl or HTTP status needed, since path/code are
    // already a complete identity. Useful for internal service-to-service calls, background jobs,
    // and anywhere else an HTTP-shaped response doesn't apply.
    val stripeCode = toCodeDetail(duplicateCharge)
    println("[kiit] path: ${stripeCode.path}")
    println("[kiit] code: ${stripeCode.code}")
    println("[kiit] message: ${stripeCode.message}")

    // A built-in Status needs no baseUrl, it defaults to kiit-codes' own https://kiit.dev/problems.
    // An Err.ErrorList populates errors[], one ProblemError per wrapped Err, in both shapes.
    val validationErr =
        Err.ErrorList(
            errors = listOf(Err.on("phone", "1234567890123", "Too long")),
            message = "Validation failed",
        )
    val validationProblem = toProblemDetail(Invalid.INVALID_VALUE, validationErr)
    val validationCode = toCodeDetail(Invalid.INVALID_VALUE, validationErr)
    println("[rfc]  validation type: ${validationProblem.type}")
    println("[kiit] validation code: ${validationCode.code}")
    println("[kiit] validation errors: ${validationCode.errors}")
}

fun validatePhone(phone: String, caller: String = "guest"): Checked {
    return when {
        phone.isEmpty()    -> {
            Checked.failure(
                Invalid.INVALID_VALUE,
                listOf(Err.on("phone", phone, "Too short")),
            )
        }
        phone.length > 10 -> {
            Checked.failure(
                Invalid.INVALID_VALUE,
                listOf(Err.on("phone", phone, "Too long")),
            )
        }
        phone == "1111111111" -> {
            Checked.failure(
                Rejected.RULE_VIOLATION,
                listOf(Err.on("phone", phone, "Reserved phone for testing")),
            )
        }
        phone.startsWith("123") && caller != "admin" -> {
            Checked.failure(
                Restricted.DENIED,
                listOf(Err.on("phone", phone, "Only admins can validate internal-use numbers")),
            )
        }
        else -> {
            Checked.success()
        }
    }
}
