/**
 * Living documentation of @kiit/codes from real TypeScript, type-checked (`npm run typecheck`)
 * against the actual native port, not a Kotlin/JS-compiled `.d.ts`. Mirrors the scenarios in
 * samples/sample-kotlin and samples/sample-java, including the RFC 9457 / kiit-native format
 * conversion from PR #29.
 */
import {
  Succeeded,
  Restricted,
  Invalid,
  Err,
  Checked,
  CodesToHttp,
  Codes,
  Groups,
  assertNever,
  RestrictedError,
  collect,
  Catalog,
  CodesToProblem,
  toCodeDetail,
} from "@kiit/codes";
import type { Passed, ErrorItem } from "@kiit/codes";

function check(condition: boolean, label: string): void {
  if (!condition) {
    throw new Error(`FAILED: ${label}`);
  }
  console.log(`ok: ${label}`);
}

// Companion constant access: Succeeded.SUCCESS, Restricted.DENIED. No `new` anywhere - every
// group is a plain object, matching Kotlin's own `Succeeded(...)`-style construction exactly.
const ok = Succeeded.SUCCESS;
const denied = Restricted.DENIED;
check(ok.name === "SUCCESS", "Succeeded.SUCCESS.name");
check(denied.name === "DENIED", "Restricted.DENIED.name");

// Err.of: optional trailing `cause` argument omitted.
const err = Err.of("email is required");
check(err.message === "email is required", "Err.of(message).message");

// CodesToHttp: no-arg factory call, no `new`.
const http = CodesToHttp();
check(http.toCode(ok) === 200, "CodesToHttp().toCode(SUCCESS) === 200");
check(http.toCode(denied) === 401, "CodesToHttp().toCode(DENIED) === 401");

// Checked.success / Checked.failure: errors is a native readonly array, no KtList wrapper needed.
const validEmail = Checked.success();
check(validEmail.isValid, "Checked.success().isValid");

const invalidEmail = Checked.failure(Invalid.BAD_REQUEST, [err]);
check(!invalidEmail.isValid, "Checked.failure(...).isValid === false");

// collect: a real rest parameter, called with individual args directly.
const combined = collect(validEmail, invalidEmail);
check(!combined.isValid, "collect(...).isValid === false");
check(combined.errors.length === 1, "collect(...).errors.length === 1");

// Codes.all / Codes.statusFor: a plain property and a namespaced lookup function, not top-level
// proxy functions - TypeScript doesn't have Kotlin/JS's "objects can't export static members"
// limitation, so there's no need for the codesAll()/codesStatusFor() workaround.
check(Codes.all.length > 0, "Codes.all.length > 0");
const found = Codes.statusFor("dev.kiit", "Succeeded", "SUCCESS");
check(found !== undefined, "Codes.statusFor('dev.kiit', 'Succeeded', 'SUCCESS') found");

// RestrictedError: construct/throw/catch, optional trailing errors/cause arguments omitted.
// A real class here, unlike Status/Err/Checked - throw/catch is inherently instanceof-based.
try {
  throw new RestrictedError(Restricted.UNAUTHENTICATED);
} catch (e) {
  check(e instanceof RestrictedError, "caught e instanceof RestrictedError");
  if (e instanceof RestrictedError) {
    check(e.status.name === "UNAUTHENTICATED", "RestrictedError.status.name");
  }
}

// Real compiler-enforced exhaustiveness over Passed's four groups - the whole reason this port
// exists. Drop a case below and `npm run typecheck` fails, not a runtime-only instanceof chain
// that only catches gaps if kept in sync by hand.
function describe(p: Passed): string {
  switch (p.group) {
    case Groups.SUCCEEDED:
      return `Succeeded: ${p.name}`;
    case Groups.PENDING:
      return `Pending: ${p.name}`;
    case Groups.EXCLUDED:
      return `Excluded: ${p.name}`;
    case Groups.INFORMATION:
      return `Information: ${p.name}`;
    default:
      return assertNever(p);
  }
}
check(describe(ok) === "Succeeded: SUCCESS", "describe(Succeeded.SUCCESS)");

// RFC 9457 / kiit-native format conversion (see PR #29 on the Kotlin side) - a custom, scoped
// domain code converted both ways.
const catalog = Catalog.of({ "com.stripe": "https://stripe.com/problems" });
const problems = CodesToProblem(catalog, http);

const duplicateCharge = Restricted(
  "DUPLICATE_CHARGE",
  "This charge has already been processed",
  "com.stripe",
  "payments.cards",
);

const problem = problems.build(duplicateCharge);
check(
  problem.type === "https://stripe.com/problems/payments.cards/restricted/duplicate-charge",
  "CodesToProblem.build(...).type",
);
check(problem.status === 401, "CodesToProblem.build(...).status");

const detail = toCodeDetail(duplicateCharge);
check(detail.path === "com.stripe:payments.cards", "toCodeDetail(...).path");
check(detail.status === undefined, "toCodeDetail(...).status is undefined without a mapping");

// A built-in kiit status needs no Catalog entry, and its `type` resolves to the real taxonomy
// page instead of a made-up path, with the code carried as a query param.
const builtInProblem = problems.build(Invalid.INVALID_VALUE);
check(
  builtInProblem.type === "https://www.kiit.dev/docs/kiit-codes?code=Failed:Invalid:INVALID_VALUE#taxonomy",
  "CodesToProblem.build(...) for a built-in kiit status",
);

// Supplying a custom error shape instead of the default ErrorDetail.
interface DetailedError extends ErrorItem {
  readonly field?: string;
  readonly message: string;
  readonly hint: string;
}
const fieldErr = Err.on("phone", "12345", "Too long");
const richProblem = problems.buildCustom<DetailedError>(duplicateCharge, fieldErr, (e) => ({
  field: e.kind === "ErrorField" ? e.field : undefined,
  message: e.message,
  hint: "check formatting",
}));
check(richProblem.detail === "Too long", "buildCustom(...) with a custom error shape");

console.log("All sample-ts checks passed.");
