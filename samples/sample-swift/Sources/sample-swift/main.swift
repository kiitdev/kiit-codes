// Canonical sample for kiit-codes from real Swift, compiled and run against the actual KiitCodes.framework
// (Kotlin/Native with SKIE). It holds the code blocks the docs show for Swift: the Tutorial steps and the Guide recipes.
//
// 1. Every example is wrapped in `// <example id="..." tags="...">` ... `// </example>`, with the same ids as the
//    Kotlin sample, so one `<Example id="..." />` shows a tab per language.
// 2. The `check(...)` calls sit outside the markers, they check the example still works.
// 3. Swift has no Kotlin default arguments, so a call passes them explicitly, such as `ex: nil`. Constants live on
//    `.companion`. Kotlin exceptions don't bridge to Swift's `Error`, so there is no exceptions recipe, and there is
//    no JSON recipe.
// 4. Run it with `./run.sh`, after building the framework with `./gradlew :kiit-codes:linkDebugFrameworkIosSimulatorArm64`.

/*
<example id="setup-imports" tags="setup">
```swift
import KiitCodes
```
</example>
*/

import Foundation
import KiitCodes

var checks = 0

/// Fails the run if a claim an example makes stops being true. Outside the example markers on purpose.
func check(_ condition: Bool, _ label: String) {
    guard condition else {
        fatalError("FAILED: \(label)")
    }
    checks += 1
    print("  ok: \(label)")
}

func section(_ title: String) {
    print("")
    print(String(repeating: "=", count: 60))
    print(title)
    print(String(repeating: "=", count: 60))
}

// ============================================================
// Tutorial: three short steps, each one a quick win
// ============================================================

func tutorialStatus() {
    section("Tutorial 1: Return a status")

    // <example id="tutorial-status" tags="tutorial">
    // A failure you expect is a value, not an exception
    func validate(_ title: String) -> Status {
        if title.trimmingCharacters(in: .whitespaces).isEmpty {
            return Failed.Invalid.companion.INVALID_VALUE
        }
        return Passed.Succeeded.companion.SUCCESS
    }

    let good = validate("buy milk")
    // success = true, group = Succeeded, name = SUCCESS
    print("success = \(good.success), group = \(good.group), name = \(good.name)")

    let bad = validate("")
    // success = false, group = Invalid, name = INVALID_VALUE
    print("success = \(bad.success), group = \(bad.group), name = \(bad.name)")
    // </example>
    check(good.success && good.group == "Succeeded" && good.name == "SUCCESS", "tutorial-status: passes")
    check(!bad.success && bad.group == "Invalid" && bad.name == "INVALID_VALUE", "tutorial-status: fails")
}

func tutorialValidate() {
    section("Tutorial 2 and 3: Validate, then build a problem")

    // <example id="tutorial-validate" tags="tutorial">
    // Checked carries the status and the errors together
    func validate(_ title: String) -> Checked {
        if !title.trimmingCharacters(in: .whitespaces).isEmpty {
            return Checked.companion.success(status: Passed.Succeeded.companion.SUCCESS)
        }
        return Checked.companion.failure(
            status: Failed.Invalid.companion.INVALID_VALUE,
            errors: [Err.companion.on(field: "title", value: title, message: "must not be blank", ex: nil)]
        )
    }

    let checked = validate("")
    // failed: INVALID_VALUE
    switch onEnum(of: checked.status) {
    case .passed(let passed):
        print("ok: \(passed.name)")
    case .failed(let failed):
        print("failed: \(failed.name)")
    }
    // </example>
    check(!checked.isValid && !checked.status.success, "tutorial-validate: failed")
    check(checked.errors.count == 1, "tutorial-validate: one error")

    // <example id="tutorial-problem" tags="tutorial,rfc9457">
    let errors = Err.ErrorList(errors: checked.errors, message: "Validation failed", cause: nil, ref: nil)

    // The failure as an RFC 9457 problem, for an HTTP API
    let converter = ProblemConverter(baseUrls: [:], mapping: CodesToHttp(overrides: [:]))
    let problem = converter.convert(status: checked.status, err: errors, typeBuilder: ProblemConverterKt.defaultTypeBuilder)
    // 400
    print(problem.status)
    // 1
    print(problem.errors?.count ?? 0)

    // The same failure as kiit's CodeDetail, self-contained, for your own services
    let detail = CodeDetailKt.toCodeDetail(status: checked.status, err: errors, mapping: nil)
    // kiit.dev:codes:Failed:Invalid:INVALID_VALUE
    print(detail.code)
    // false
    print(detail.success)
    // </example>
    check(problem.type == "https://www.kiit.dev/docs/kiit-codes?code=Failed:Invalid:INVALID_VALUE#taxonomy", "tutorial-problem: type")
    check(problem.title == "The request had an invalid value." && problem.status == 400, "tutorial-problem: title and status")
    check(problem.detail == "Validation failed" && problem.errors?.count == 1, "tutorial-problem: detail and errors")
    check(problem.code == "kiit.dev:codes:Failed:Invalid:INVALID_VALUE", "tutorial-problem: code")
    check(detail.code == "kiit.dev:codes:Failed:Invalid:INVALID_VALUE" && !detail.success, "tutorial-problem: detail code and success")
    check(detail.title == "The request had an invalid value." && detail.detail == "Validation failed", "tutorial-problem: detail title and text")
    check(detail.errors?.count == 1, "tutorial-problem: detail errors")
}

// ============================================================
// Guide: recipes. Each one stands alone, so the page can show it as it is.
// ============================================================

func guideBuiltins() {
    section("Guide: Status: Built-ins")

    // <example id="guide-builtins" tags="guide">
    // A specific built-in code
    let created = Passed.Succeeded.companion.CREATED
    // The group's default, when you only know the kind of outcome
    let failed = Failed.Invalid.companion.DEFAULT
    // INVALID_VALUE
    print(failed.name)
    // true
    print(failed.isDefault)
    // false
    print(Failed.Invalid.companion.BAD_REQUEST.isDefault)
    // </example>
    check(created.name == "CREATED", "guide-builtins: created")
    check(failed.name == "INVALID_VALUE" && failed.isDefault, "guide-builtins: default")
    check(!Failed.Invalid.companion.BAD_REQUEST.isDefault, "guide-builtins: not default")
}

func guideCustomCode() {
    section("Guide: Status: Custom code")

    // <example id="guide-custom-code" tags="guide">
    let PAYMENT_DECLINED = Failed.Rejected(
        name: "PAYMENT_DECLINED",
        title: "Payment declined",
        origin: "payments.example.com",
        scope: "payments.cards"
    )
    // </example>
    check(PAYMENT_DECLINED.group == "Rejected", "guide-custom-code: group")
    check(PAYMENT_DECLINED.origin == "payments.example.com" && PAYMENT_DECLINED.scope == "payments.cards", "guide-custom-code: origin and scope")
}

func guidePatternMatching() {
    section("Guide: Status: Pattern matching")

    // <example id="guide-pattern-matching" tags="guide">
    // 1. Passed or Failed
    func binary(_ status: Status) -> String {
        switch onEnum(of: status) {
        case .passed(let passed): return "ok: \(passed.name)"
        case .failed(let failed): return "failed: \(failed.name)"
        }
    }

    // 2. Passed or Failed, each by its four groups. Two switches, one per sealed type, both exhaustive.
    func nested(_ status: Status) -> String {
        switch onEnum(of: status) {
        case .passed(let passed):
            switch onEnum(of: passed) {
            case .succeeded: return "done"
            case .pending: return "in progress"
            case .excluded: return "skipped"
            case .information: return "for your information"
            }
        case .failed(let failed):
            switch onEnum(of: failed) {
            case .restricted: return "not allowed"
            case .invalid: return "fix the input"
            case .rejected: return "refused by a rule"
            case .unserved: return "try again later"
            }
        }
    }

    // 3. Specific to broad: a code, then a group, then Failed or Passed
    func hybrid(_ status: Status) -> String {
        if let rejected = status as? Failed.Rejected, rejected.name == "CONFLICT" {
            return "already exists"
        }
        if status is Failed.Invalid {
            return "fix the input"
        }
        if !status.success {
            return "failed: \(status.name)"
        }
        return "ok: \(status.name)"
    }

    // failed: INVALID_VALUE
    print(binary(Failed.Invalid.companion.INVALID_VALUE))
    // in progress
    print(nested(Passed.Pending.companion.QUEUED))
    // already exists
    print(hybrid(Failed.Rejected.companion.CONFLICT))
    // </example>
    check(binary(Failed.Invalid.companion.INVALID_VALUE) == "failed: INVALID_VALUE" && binary(Passed.Succeeded.companion.SUCCESS) == "ok: SUCCESS", "guide-pattern-matching: binary")
    check(nested(Passed.Succeeded.companion.SUCCESS) == "done" && nested(Passed.Pending.companion.QUEUED) == "in progress", "guide-pattern-matching: nested passed")
    check(nested(Passed.Excluded.companion.SKIPPED) == "skipped" && nested(Passed.Information.companion.NOTICE) == "for your information", "guide-pattern-matching: nested passed rest")
    check(nested(Failed.Invalid.companion.INVALID_VALUE) == "fix the input" && nested(Failed.Unserved.companion.TIMEOUT) == "try again later", "guide-pattern-matching: nested failed")
    check(nested(Failed.Restricted.companion.DENIED) == "not allowed" && nested(Failed.Rejected.companion.CONFLICT) == "refused by a rule", "guide-pattern-matching: nested failed rest")
    check(hybrid(Failed.Rejected.companion.CONFLICT) == "already exists", "guide-pattern-matching: hybrid code first")
    check(hybrid(Failed.Invalid.companion.INVALID_VALUE) == "fix the input", "guide-pattern-matching: hybrid group")
    check(hybrid(Failed.Unserved.companion.TIMEOUT) == "failed: TIMEOUT" && hybrid(Passed.Succeeded.companion.SUCCESS) == "ok: SUCCESS", "guide-pattern-matching: hybrid broad")
}

func guideCollectErrors() {
    section("Guide: Error Handling: Collect errors")

    // <example id="guide-collect-errors" tags="guide">
    func validateTitle(_ title: String) -> Checked {
        if !title.trimmingCharacters(in: .whitespaces).isEmpty {
            return Checked.companion.success(status: Passed.Succeeded.companion.SUCCESS)
        }
        return Checked.companion.failure(
            status: Failed.Invalid.companion.INVALID_VALUE,
            errors: [Err.companion.on(field: "title", value: title, message: "must not be blank", ex: nil)]
        )
    }

    func validateListId(_ listId: String) -> Checked {
        if ["personal", "team"].contains(listId) {
            return Checked.companion.success(status: Passed.Succeeded.companion.SUCCESS)
        }
        return Checked.companion.failure(
            status: Failed.Invalid.companion.NOT_FOUND,
            errors: [Err.companion.on(field: "listId", value: listId, message: "unknown list", ex: nil)]
        )
    }

    let checked = collect(checks: [validateTitle(""), validateListId("unknown-list")])
    // valid = false, errors = 2
    print("valid = \(checked.isValid), errors = \(checked.errors.count)")
    // </example>
    check(!checked.isValid, "guide-collect-errors: invalid")
    check(checked.errors.count == 2, "guide-collect-errors: two errors")
}

func guideErrorDetails() {
    section("Guide: Error Handling: Error details")

    // <example id="guide-error-details" tags="guide">
    // A plain message
    let plain = Err.companion.of(message: "title is required", ex: nil)
    // An error on one field, with its value
    let title = Err.companion.on(field: "title", value: "", message: "must be 1-100 characters", ex: nil)
    // The same, without the value
    let password = Err.companion.on(field: "password", message: "must be at least 12 characters", ex: nil)
    // Several plain messages under one message
    let many = Err.companion.list(errors: ["title is required", "list is unknown"], message: "Validation failed")
    // </example>
    check(plain.message == "title is required", "guide-error-details: plain")
    check((title as? Err.ErrorField)?.field == "title", "guide-error-details: field")
    check((password as? Err.ErrorField)?.field == "password", "guide-error-details: no value")
    check(many.errors.count == 2 && many.message == "Validation failed", "guide-error-details: list")
}

func guideTypeUrl() {
    section("Guide: Response: Type URL")

    // <example id="guide-type-url" tags="guide">
    let stripe = Failed.Rejected(name: "DUPLICATE_CHARGE", title: "Duplicate charge", origin: "stripe.com", scope: "payments.cards")
    let plain = Failed.Rejected(name: "OUT_OF_STOCK", title: "Out of stock", origin: "myapp1", scope: "")
    let http = CodesToHttp(overrides: [:])
    let builder = ProblemConverterKt.defaultTypeBuilder

    // 1. A domain origin and nothing registered
    // https://stripe.com/docs/codes/payments/cards/failed/rejected/duplicate-charge
    let problems = ProblemConverter(baseUrls: [:], mapping: http)
    print(problems.convert(status: stripe, err: nil, typeBuilder: builder).type)

    // 2. A plain id is not a domain, so the type is relative
    // /docs/codes/failed/rejected/out-of-stock
    print(problems.convert(status: plain, err: nil, typeBuilder: builder).type)

    // 3. A base URL registered for the origin
    // https://stripe.com/errors/payments/cards/failed/rejected/duplicate-charge
    let registered = ProblemConverter(baseUrls: ["stripe.com": "https://stripe.com/errors"], mapping: http)
    print(registered.convert(status: stripe, err: nil, typeBuilder: builder).type)
    // </example>
    check(problems.convert(status: stripe, err: nil, typeBuilder: builder).type == "https://stripe.com/docs/codes/payments/cards/failed/rejected/duplicate-charge", "guide-type-url: domain")
    check(problems.convert(status: plain, err: nil, typeBuilder: builder).type == "/docs/codes/failed/rejected/out-of-stock", "guide-type-url: plain id")
    check(registered.convert(status: stripe, err: nil, typeBuilder: builder).type == "https://stripe.com/errors/payments/cards/failed/rejected/duplicate-charge", "guide-type-url: registered")
}

func guideCustomTypeUrl() {
    section("Guide: Response: Custom type URL")

    // <example id="guide-custom-type-url" tags="guide">
    let stripe = Failed.Rejected(name: "DUPLICATE_CHARGE", title: "Duplicate charge", origin: "stripe.com", scope: "payments.cards")
    let problems = ProblemConverter(baseUrls: [:], mapping: CodesToHttp(overrides: [:]))

    // 1. Your own suffix, with a type builder
    // https://stripe.com/docs/codes/charges/duplicate
    print(problems.convert(status: stripe, err: nil, typeBuilder: { _ in "charges/duplicate" }).type)

    // 2. Exactly one URL, a base with an empty suffix
    // https://example.com/probs/duplicate-charge
    print(problems.convertWithUrl(status: stripe, err: nil, baseUrl: "https://example.com/probs/duplicate-charge", typeBuilder: { _ in "" }).type)
    // </example>
    check(problems.convert(status: stripe, err: nil, typeBuilder: { _ in "charges/duplicate" }).type == "https://stripe.com/docs/codes/charges/duplicate", "guide-custom-type-url: builder")
    check(problems.convertWithUrl(status: stripe, err: nil, baseUrl: "https://example.com/probs/duplicate-charge", typeBuilder: { _ in "" }).type == "https://example.com/probs/duplicate-charge", "guide-custom-type-url: exact")
}

func guideHttp() {
    section("Guide: Response: HTTP and gRPC")

    // <example id="guide-http" tags="guide">
    let http = CodesToHttp(overrides: CodesToHttp.companion.DEFAULT_OVERRIDES)
    let grpc = CodesToGrpc(overrides: CodesToGrpc.companion.DEFAULT_OVERRIDES)

    // 201
    print(http.toCode(status: Passed.Succeeded.companion.CREATED))
    // 409
    print(http.toCode(status: Failed.Rejected.companion.CONFLICT))

    // 0 (OK)
    print(grpc.toCode(status: Passed.Succeeded.companion.CREATED))
    // 6 (ALREADY_EXISTS)
    print(grpc.toCode(status: Failed.Rejected.companion.CONFLICT))
    // </example>
    check(http.toCode(status: Passed.Succeeded.companion.CREATED) == 201 && http.toCode(status: Failed.Rejected.companion.CONFLICT) == 409, "guide-http: http")
    check(grpc.toCode(status: Passed.Succeeded.companion.CREATED) == 0 && grpc.toCode(status: Failed.Rejected.companion.CONFLICT) == 6, "guide-http: grpc")
}

func guideCustomProtocol() {
    section("Guide: Response: Custom protocol")

    // <example id="guide-custom-protocol" tags="guide">
    let PAYMENT_DECLINED = Failed.Rejected(name: "PAYMENT_DECLINED", title: "Payment declined", origin: "payments.example.com", scope: "")

    let http = CompositeLookup(
        base: CodesToHttp(overrides: CodesToHttp.companion.DEFAULT_OVERRIDES),
        extensions: [PAYMENT_DECLINED: KotlinInt(int: 402)]
    )
    // 402
    print(http.toCode(status: PAYMENT_DECLINED))
    // 409
    print(http.toCode(status: Failed.Rejected.companion.CONFLICT))
    // </example>
    check(http.toCode(status: PAYMENT_DECLINED) == 402, "guide-custom-protocol: extension")
    check(http.toCode(status: Failed.Rejected.companion.CONFLICT) == 409, "guide-custom-protocol: falls back to the base")
}

tutorialStatus()
tutorialValidate()
guideBuiltins()
guideCustomCode()
guidePatternMatching()
guideCollectErrors()
guideErrorDetails()
guideTypeUrl()
guideCustomTypeUrl()
guideHttp()
guideCustomProtocol()

print("")
print("All \(checks) checks passed.")
