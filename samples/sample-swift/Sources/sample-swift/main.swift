// Living documentation of kiit-codes from real Swift, compiled and run against the actual
// KiitCodes.framework (Kotlin/Native). SKIE compiles its generated Swift wrapper code (onEnum,
// the __Sealed enums, etc.) directly into the framework's own swiftmodule, so `import KiitCodes`
// is all that's needed. Mirrors the scenarios in
// kiit-codes/src/jvmTest/java/kiit/codes/JavaInteropTest.java, samples/sample-java, and
// samples/sample-ts, adapted to what SKIE actually generates.
import Foundation
import KiitCodes

func check(_ condition: Bool, _ label: String) {
    guard condition else {
        fatalError("FAILED: \(label)")
    }
    print("ok: \(label)")
}

// Companion constant access: unlike @JvmField (Java), plain Kotlin/Native interop has no
// flattening mechanism. Companion members always go through `.companion`, with no equivalent
// annotation to make them directly static. Every leaf type exposes
// `class var companion: XxxCompanion { get }`, not the members directly.
let ok = Passed.Succeeded.companion.SUCCESS
let denied = Failed.Restricted.companion.DENIED
check(ok.name == "SUCCESS", "Passed.Succeeded.companion.SUCCESS.name")
check(denied.name == "DENIED", "Failed.Restricted.companion.DENIED.name")

// Err: companion factory functions, same `.companion` pattern.
let err = Err.companion.of(message: "email is required", ex: nil)
check(err.message == "email is required", "Err.companion.of(message:ex:).message")

// CodesToHttp: Kotlin's default-valued constructor doesn't get an automatic zero-arg
// convenience initializer preserved for Swift. `init()` compiles but is marked `unavailable`
// in the header, the same kind of gap `@JvmOverloads` fixes for Java. Callers must pass the
// full argument explicitly.
let http = CodesToHttp(overrides: [:])
check(http.toCode(status: ok) == 200, "CodesToHttp.toCode(SUCCESS) == 200")
check(http.toCode(status: denied) == 401, "CodesToHttp.toCode(DENIED) == 401")

// Checked.success / Checked.failure: SKIE bridges Swift arrays directly, no manual
// KotlinArray/NSArray wrapping needed (nicer than the JS side, which needs KtList.fromJsArray).
let validEmail = Checked.companion.success(status: ok)
check(validEmail.isValid, "Checked.companion.success(status:).isValid")

let invalidEmail = Checked.companion.failure(status: Failed.Invalid.companion.BAD_REQUEST, errors: [err])
check(!invalidEmail.isValid, "Checked.companion.failure(...).isValid == false")

// collect: SKIE-generated Swift wrapper takes a native Swift array.
let combined = collect(checks: [validEmail, invalidEmail])
check(!combined.isValid, "collect(checks:).isValid == false")
check(combined.errors.count == 1, "collect(checks:).errors.count == 1")

// codesAll / codesStatusFor: SKIE-generated wrapper functions for the Codes object. Plain
// Kotlin `object`s don't get clean static member access even with SKIE, same gap as JS. The
// codesAll()/codesStatusFor() proxies from Codes.kt are what make these reachable at all.
let all = codesAll()
check(all.count > 0, "codesAll().count > 0")
let found = codesStatusFor(origin: "kiit", name: "SUCCESS")
check(found != nil, "codesStatusFor(origin:name:) found")

// RestrictedError works fine to construct and read from. Throwing one manually doesn't.
//
// 1. `throw RestrictedError(...) as! Error` compiles, since the compiler treats the Error
//    conformance as a cast it can't check statically, but it crashes at runtime:
//      "Could not cast value of type 'KiitCodesRestrictedError' ... to 'Swift.Error'"
// 2. Kotlin exception types just don't bridge to Swift's Error protocol when constructed and
//    thrown by hand, even though StatusError.kt's own KDoc shows this exact pattern as its
//    fallback. The real feature it's describing is @Throws-annotated Kotlin functions bridging
//    to native `throws`, and kiit-codes doesn't have one of those yet.
let restrictedError = RestrictedError(status: Failed.Restricted.companion.UNAUTHENTICATED, errors: [], cause: nil)
check(restrictedError.status.name == "UNAUTHENTICATED", "RestrictedError(status:).status.name")

// Exhaustiveness is the actual point of adding SKIE.
//
// 1. SKIE generates one Swift enum per Kotlin sealed type, not a single flattened enum for the
//    whole Status -> Passed/Failed -> leaf-type hierarchy. Getting from a `Status` down to a
//    concrete leaf type takes two nested `onEnum(of:)` switches, each independently exhaustive.
// 2. Still a real upgrade over plain Kotlin/Native interop, which gives zero exhaustiveness, and
//    over TypeScript, which also gives zero and needs a hand-maintained union. Just nested here,
//    not flat.
func describe(_ status: Status) -> String {
    switch onEnum(of: status) {
    case .passed(let passed):
        switch onEnum(of: passed) {
        case .succeeded(let s): return "Succeeded: \(s.name)"
        case .pending(let p): return "Pending: \(p.name)"
        case .excluded(let e): return "Excluded: \(e.name)"
        case .information(let i): return "Information: \(i.name)"
        }
    case .failed(let failed):
        switch onEnum(of: failed) {
        case .restricted(let r): return "Restricted: \(r.name)"
        case .invalid(let i): return "Invalid: \(i.name)"
        case .rejected(let r): return "Rejected: \(r.name)"
        case .unserved(let u): return "Unserved: \(u.name)"
        }
    }
    // No `default:` needed at either level, both switches are exhaustive over their own
    // sealed type. Try commenting out any single case above: it's a compile error, not a
    // runtime surprise, at both levels independently.
}

check(describe(ok) == "Succeeded: SUCCESS", "describe(Succeeded.SUCCESS)")
check(describe(denied) == "Restricted: DENIED", "describe(Restricted.DENIED)")

print("All sample-swift checks passed.")
