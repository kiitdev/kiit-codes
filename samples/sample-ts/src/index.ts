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

/*
<example id="setup-imports" tags="setup">
```ts
import { Succeeded, Restricted } from "{{module.package}}";
import type { Status } from "{{module.package}}";
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
  ErrorList,
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
  const EMPTY_TITLE = Invalid("EMPTY_TITLE", "Title must not be empty", "samples.kiit.dev");
  const DUPLICATE_TASK = Rejected("DUPLICATE_TASK", "That task is already on the list", "samples.kiit.dev", "lists.team");

  // <example id="rfc9457-minimal" tags="rfc9457,conversion">
  // A built-in status as an RFC 9457 problem. Built-in codes point at the kiit taxonomy page.
  const problems = ProblemConverter();
  const minimal = problems.convert(Rejected.CONFLICT);
  console.log(minimal.type); // https://www.kiit.dev/docs/kiit-codes?code=Failed:Rejected:CONFLICT#taxonomy
  console.log(minimal.title); // The request conflicts with the current state.
  console.log(minimal.status); // 409
  // </example>
  check(minimal.type === "https://www.kiit.dev/docs/kiit-codes?code=Failed:Rejected:CONFLICT#taxonomy", "rfc9457-minimal: type");
  check(minimal.title === Rejected.CONFLICT.message, "rfc9457-minimal: title");
  check(minimal.status === 409, "rfc9457-minimal: status");

  // <example id="rfc9457-convert" tags="rfc9457,conversion">
  // A status and a list of errors become an RFC 9457 problem
  const failures = ErrorList(
    [Err.on("title", "", "must be 1-100 characters"), Err.on("listId", "x", "unknown list")],
    "Validation failed",
  );
  const problem = ProblemConverter().convert(Invalid.INVALID_VALUE, failures);

  console.log(problem.status); // 400
  console.log(problem.detail); // Validation failed
  console.log(problem.errors?.length); // 2
  // </example>
  check(problem.status === 400, "rfc9457-convert: status");
  check(problem.detail === "Validation failed", "rfc9457-convert: detail");
  check(problem.errors?.length === 2, "rfc9457-convert: errors");

  // <example id="rfc9457-problem" tags="rfc9457,json">
  // A validation failure as an RFC 9457 problem, written as JSON
  const fieldErrors = ErrorList(
    [Err.on("title", "", "must be 1-100 characters"), Err.on("listId", "x", "unknown list")],
    "Validation failed",
  );
  const body = ProblemConverter().convert(Invalid.INVALID_VALUE, fieldErrors);

  console.log(JSON.stringify(body, null, 2));
  // </example>
  check(body.status === 400, "rfc9457-problem: status");
  check(body.errors?.length === 2, "rfc9457-problem: errors");

  // <example id="rfc9457-errors" tags="rfc9457,usage">
  // An Err.ErrorList fills detail and errors (errors is a kiit extension member, not part of the RFC)
  const validation = ErrorList(
    [Err.on("title", "", "must be 1-100 characters"), Err.on("listId", "x", "unknown list")],
    "Validation failed",
  );
  const withErrors = problems.convert(Invalid.INVALID_VALUE, validation);
  console.log(`${withErrors.detail}, ${withErrors.errors?.length} errors`); // Validation failed, 2 errors
  // </example>
  check(withErrors.detail === "Validation failed", "rfc9457-errors: detail");
  check(withErrors.errors?.length === 2, "rfc9457-errors: errors");

  // <example id="rfc9457-domain-origin" tags="rfc9457,origin">
  // A custom code whose origin is a domain needs no registration: https://{origin}/problems/{group}/{name}
  const domain = problems.convert(EMPTY_TITLE);
  console.log(domain.type); // https://samples.kiit.dev/problems/invalid/empty-title
  // </example>
  check(domain.type === "https://samples.kiit.dev/problems/invalid/empty-title", "rfc9457-domain-origin: type");

  // <example id="rfc9457-registered" tags="rfc9457,origin">
  // Register a base URL when the docs live somewhere else. An entry wins over the origin.
  const registered = ProblemConverter({ "samples.kiit.dev": "https://docs.samples.kiit.dev/errors" });
  console.log(registered.convert(EMPTY_TITLE).type); // https://docs.samples.kiit.dev/errors/invalid/empty-title
  // </example>
  check(
    registered.convert(EMPTY_TITLE).type === "https://docs.samples.kiit.dev/errors/invalid/empty-title",
    "rfc9457-registered: type",
  );

  // <example id="rfc9457-scope" tags="rfc9457">
  // A scope becomes the first path segment
  const scoped = problems.convert(DUPLICATE_TASK);
  console.log(scoped.type); // https://samples.kiit.dev/problems/lists.team/rejected/duplicate-task
  // </example>
  check(scoped.type === "https://samples.kiit.dev/problems/lists.team/rejected/duplicate-task", "rfc9457-scope: type");

  // <example id="rfc9457-custom-item" tags="rfc9457">
  // When field + message isn't enough, map each error into your own type
  interface RichError extends ErrorItem {
    readonly field?: string;
    readonly message: string;
    readonly hint: string;
  }
  const rich = problems.convertCustom<RichError>(Restricted.FORBIDDEN, validation, (e) => ({
    field: e.kind === "ErrorField" ? e.field : undefined,
    message: e.message,
    hint: "ask the list owner instead",
  }));
  console.log(rich.errors?.[0]); // { field: 'title', message: 'must be 1-100 characters', hint: 'ask the list owner instead' }
  // </example>
  check(rich.errors?.[0]?.hint === "ask the list owner instead", "rfc9457-custom-item: hint");
  check(rich.errors?.length === 2, "rfc9457-custom-item: count");

  // <example id="rfc9457-vs-codedetail" tags="rfc9457,conversion">
  // The same status as an RFC 9457 problem (for an HTTP API) and as kiit's own CodeDetail (service to service)
  const forbidden = Restricted.FORBIDDEN;
  const asProblem = ProblemConverter().convert(forbidden);
  const asDetail = toCodeDetail(forbidden, undefined, CodesToHttp());

  console.log(JSON.stringify(asProblem, null, 2));
  // {
  //   "type": "https://www.kiit.dev/docs/kiit-codes?code=Failed:Restricted:FORBIDDEN#taxonomy",
  //   "title": "Access to this resource is forbidden.",
  //   "status": 403
  // }

  console.log(JSON.stringify(asDetail, null, 2));
  // {
  //   "path": "kiit.dev",
  //   "code": "Failed:Restricted:FORBIDDEN",
  //   "success": false,
  //   "message": "Access to this resource is forbidden.",
  //   "status": 403
  // }
  // </example>
  check(asProblem.status === 403 && asDetail.status === 403, "rfc9457-vs-codedetail: same status");
  check(asDetail.code === "Failed:Restricted:FORBIDDEN", "rfc9457-vs-codedetail: code");
  check(asDetail.path === "kiit.dev", "rfc9457-vs-codedetail: path");

  // <example id="rfc9457-plain-origin" tags="rfc9457,origin">
  // A plain id is used as is. It looks like a host, so register a base URL or use a domain.
  const plain = Rejected("OUT_OF_STOCK", "Out of stock", "myapp1");
  console.log(problems.convert(plain).type); // https://myapp1/problems/rejected/out-of-stock
  const fixed = ProblemConverter({ myapp1: "https://docs.example.com/myapp1/errors" });
  console.log(fixed.convert(plain).type); // https://docs.example.com/myapp1/errors/rejected/out-of-stock
  // </example>
  check(problems.convert(plain).type === "https://myapp1/problems/rejected/out-of-stock", "rfc9457-plain-origin: as is");
  check(
    fixed.convert(plain).type === "https://docs.example.com/myapp1/errors/rejected/out-of-stock",
    "rfc9457-plain-origin: registered",
  );

  // Not in the docs: a custom, scoped code converted both ways.
  const stripe = ProblemConverter({ "stripe.com": "https://stripe.com/errors" }, CodesToHttp());

  const duplicateCharge = Restricted(
    "DUPLICATE_CHARGE",
    "This charge has already been processed",
    "stripe.com",
    "payments.cards",
  );

  const stripeProblem = stripe.convert(duplicateCharge);
  check(
    stripeProblem.type === "https://stripe.com/errors/payments.cards/restricted/duplicate-charge",
    "ProblemConverter.convert(...).type",
  );
  check(stripeProblem.status === 401, "ProblemConverter.convert(...).status");

  const detail = toCodeDetail(duplicateCharge);
  check(detail.path === "stripe.com:payments.cards", "toCodeDetail(...).path");
  check(detail.status === undefined, "toCodeDetail(...).status is undefined without a mapping");

  // A built-in kiit status needs no baseUrls entry, and its `type` resolves to the real taxonomy
  // page instead of a made-up path, with the code carried as a query param.
  const builtInProblem = stripe.convert(Invalid.INVALID_VALUE);
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
  const richProblem = stripe.convertCustom<DetailedError>(duplicateCharge, fieldErr, (e) => ({
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
