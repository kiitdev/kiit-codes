/**
 * Living documentation of @kiitdev/codes from real TypeScript, type-checked (`npm run typecheck`)
 * against the actual native port, not a Kotlin/JS-compiled `.d.ts`. Mirrors the scenarios in
 * samples/sample-kotlin and samples/sample-java, including the RFC 9457 / kiit-native format
 * conversion from PR #29.
 *
 * Organized into one function per logical area, run in order at the bottom of this file.
 */
/*
<example id="setup-install" tags="setup">
```bash title="terminal"
npm install {{module.package}}
```
</example>
*/

import {
  Succeeded,
  Pending,
  Excluded,
  Information,
  Restricted,
  Invalid,
  Rejected,
  Unserved,
  Err,
  Checked,
  CodesToHttp,
  Codes,
  Groups,
  assertNever,
  RestrictedError,
  collect,
  ProblemConverter,
  toCodeDetail,
} from "@kiitdev/codes";
import type { Status, Passed, Failed, ErrorItem } from "@kiitdev/codes";

function check(condition: boolean, label: string): void {
  if (!condition) {
    throw new Error(`FAILED: ${label}`);
  }
  console.log(`ok: ${label}`);
}

const SEPARATOR = "=".repeat(60);

function section(title: string): void {
  console.log(`\n${SEPARATOR}`);
  console.log(title);
  console.log(SEPARATOR);
}

/**
 * Construction, HTTP mapping, and the built-in registry. No `new` anywhere - every group is a
 * plain object, matching Kotlin's own `Succeeded(...)`-style construction exactly.
 */
function showCoreFunctionality(): void {
  section("Core functionality");
  const ok = Succeeded.SUCCESS;
  const denied = Restricted.DENIED;
  check(ok.name === "SUCCESS", "Succeeded.SUCCESS.name");
  check(denied.name === "DENIED", "Restricted.DENIED.name");

  const http = CodesToHttp();
  check(http.toCode(ok) === 200, "CodesToHttp().toCode(SUCCESS) === 200");
  check(http.toCode(denied) === 401, "CodesToHttp().toCode(DENIED) === 401");

  // Codes.all / Codes.statusFor: a plain property and a namespaced lookup function - real object
  // statics, reachable directly, no top-level proxy functions needed.
  check(Codes.all.length > 0, "Codes.all.length > 0");
  const found = Codes.statusFor("kiit.dev", "Succeeded", "SUCCESS");
  check(found !== undefined, "Codes.statusFor('kiit.dev', 'Succeeded', 'SUCCESS') found");
}

/**
 * Real compiler-enforced exhaustiveness at the coarse Passed/Failed split, narrowing on
 * `.success`. No `assertNever` needed here specifically - `success` is a real boolean, only two
 * values ever possible.
 */
function showExhaustivenessOverPassedAndFailed(): void {
  section("Exhaustiveness: Passed vs Failed");
  function describeOutcome(status: Status): string {
    if (status.success) {
      return `passed: ${status.name}`; // status: Passed here
    } else {
      return `failed: ${status.name}`; // status: Failed here
    }
  }
  check(describeOutcome(Succeeded.SUCCESS) === "passed: SUCCESS", "describeOutcome narrows to Passed");
  check(describeOutcome(Restricted.DENIED) === "failed: DENIED", "describeOutcome narrows to Failed");
}

/**
 * Real compiler-enforced exhaustiveness over Passed's four groups - the whole reason this port
 * exists. Drop a case below and `npm run typecheck` fails, not a runtime-only instanceof chain
 * that only catches gaps if kept in sync by hand.
 */
function showExhaustivenessOverPassedGroups(): void {
  section("Exhaustiveness: Passed groups");
  function describe(status: Passed): string {
    switch (status.group) {
      case Groups.SUCCEEDED:
        return `Succeeded: ${status.name}`;
      case Groups.PENDING:
        return `Pending: ${status.name}`;
      case Groups.EXCLUDED:
        return `Excluded: ${status.name}`;
      case Groups.INFORMATION:
        return `Information: ${status.name}`;
      default:
        return assertNever(status);
    }
  }
  check(describe(Succeeded.SUCCESS) === "Succeeded: SUCCESS", "describe(Succeeded.SUCCESS)");
  check(describe(Pending.ACCEPTED) === "Pending: ACCEPTED", "describe(Pending.ACCEPTED)");
  check(describe(Excluded.SKIPPED) === "Excluded: SKIPPED", "describe(Excluded.SKIPPED)");
  check(describe(Information.NOTICE) === "Information: NOTICE", "describe(Information.NOTICE)");
}

/** Same exhaustiveness guarantee over Failed's four groups. */
function showExhaustivenessOverFailedGroups(): void {
  section("Exhaustiveness: Failed groups");
  function describe(status: Failed): string {
    switch (status.group) {
      case Groups.RESTRICTED:
        return `Restricted: ${status.name}`;
      case Groups.INVALID:
        return `Invalid: ${status.name}`;
      case Groups.REJECTED:
        return `Rejected: ${status.name}`;
      case Groups.UNSERVED:
        return `Unserved: ${status.name}`;
      default:
        return assertNever(status);
    }
  }
  check(describe(Restricted.DENIED) === "Restricted: DENIED", "describe(Restricted.DENIED)");
  check(describe(Invalid.BAD_REQUEST) === "Invalid: BAD_REQUEST", "describe(Invalid.BAD_REQUEST)");
  check(describe(Rejected.CONFLICT) === "Rejected: CONFLICT", "describe(Rejected.CONFLICT)");
  check(describe(Unserved.TIMEOUT) === "Unserved: TIMEOUT", "describe(Unserved.TIMEOUT)");
}

/**
 * Err builders, and the StatusError hierarchy for crossing a call boundary that can only
 * communicate via exceptions. A real class there, unlike Status/Err/Checked - throw/catch is
 * inherently instanceof-based.
 */
function showErrors(): void {
  section("Errors");
  const err = Err.of("email is required");
  check(err.message === "email is required", "Err.of(message).message");

  const fieldErr = Err.on("phone", "12345", "Too long");
  check(fieldErr.kind === "ErrorField" && fieldErr.field === "phone", "Err.on(...) builds an ErrorField");

  try {
    throw new RestrictedError(Restricted.UNAUTHENTICATED);
  } catch (e) {
    check(e instanceof RestrictedError, "caught e instanceof RestrictedError");
    if (e instanceof RestrictedError) {
      check(e.status.name === "UNAUTHENTICATED", "RestrictedError.status.name");
    }
  }
}

/** Checked.success / Checked.failure and collect. Errors are native readonly arrays throughout. */
function showChecked(): void {
  section("Checked");
  const validEmail = Checked.success();
  check(validEmail.isValid, "Checked.success().isValid");

  const invalidEmail = Checked.failure(Invalid.BAD_REQUEST, [Err.of("email is required")]);
  check(!invalidEmail.isValid, "Checked.failure(...).isValid === false");

  // collect: a real rest parameter, called with individual args directly.
  const combined = collect(validEmail, invalidEmail);
  check(!combined.isValid, "collect(...).isValid === false");
  check(combined.errors.length === 1, "collect(...).errors.length === 1");
}

/**
 * RFC 9457 / kiit-native format conversion (see PR #29 on the Kotlin side) - a custom, scoped
 * domain code converted both ways.
 */
function showFormats(): void {
  section("Formats: RFC 9457 problem conversion");
  const problems = ProblemConverter({ "stripe.com": "https://stripe.com/errors" }, CodesToHttp());

  const duplicateCharge = Restricted(
    "DUPLICATE_CHARGE",
    "This charge has already been processed",
    "stripe.com",
    "payments.cards",
  );

  const problem = problems.convert(duplicateCharge);
  check(
    problem.type === "https://stripe.com/errors/payments.cards/restricted/duplicate-charge",
    "ProblemConverter.convert(...).type",
  );
  check(problem.status === 401, "ProblemConverter.convert(...).status");

  const detail = toCodeDetail(duplicateCharge);
  check(detail.path === "stripe.com:payments.cards", "toCodeDetail(...).path");
  check(detail.status === undefined, "toCodeDetail(...).status is undefined without a mapping");

  // A built-in kiit status needs no baseUrls entry, and its `type` resolves to the real taxonomy
  // page instead of a made-up path, with the code carried as a query param.
  const builtInProblem = problems.convert(Invalid.INVALID_VALUE);
  check(
    builtInProblem.type === "https://www.kiit.dev/docs/kiit-codes?code=Failed:Invalid:INVALID_VALUE#taxonomy",
    "ProblemConverter.convert(...) for a built-in kiit status",
  );

  // Supplying a custom error shape instead of the default ErrorDetail.
  interface DetailedError extends ErrorItem {
    readonly field?: string;
    readonly message: string;
    readonly hint: string;
  }
  const fieldErr = Err.on("phone", "12345", "Too long");
  const richProblem = problems.convertCustom<DetailedError>(duplicateCharge, fieldErr, (e) => ({
    field: e.kind === "ErrorField" ? e.field : undefined,
    message: e.message,
    hint: "check formatting",
  }));
  check(richProblem.detail === "Too long", "convertCustom(...) with a custom error shape");
}

showCoreFunctionality();
showExhaustivenessOverPassedAndFailed();
showExhaustivenessOverPassedGroups();
showExhaustivenessOverFailedGroups();
showErrors();
showChecked();
showFormats();

console.log(`\n${SEPARATOR}`);
console.log("All sample-ts checks passed.");
console.log(SEPARATOR);
