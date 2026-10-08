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
  isDefault,
  toError,
  RejectedError,
} from "@kiitdev/codes";
import type { Status, Passed, Failed, ErrorItem, CodeLookup } from "@kiitdev/codes";

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

  // Every error goes into errors, and an ErrorList is expanded (errors is a kiit extension member, not part of the RFC)
  const validation = ErrorList(
    [
      Err.on("title", "", "must be 1-100 characters"),
      Err.on("listId", "x", "unknown list"),
    ],
    "Validation failed",
  );
  const withErrors = problems.convert(Invalid.INVALID_VALUE, validation);
  console.log(`${withErrors.detail}, ${withErrors.errors?.length} errors`); // Validation failed, 2 errors
  // </example>
  check(minimal.type === "https://www.kiit.dev/docs/kiit-codes?code=Failed:Rejected:CONFLICT#taxonomy", "rfc9457-minimal: type");
  check(minimal.title === Rejected.CONFLICT.title, "rfc9457-minimal: title");
  check(minimal.status === 409, "rfc9457-minimal: status");
  check(withErrors.detail === "Validation failed", "rfc9457-minimal: detail");
  check(withErrors.errors?.length === 2, "rfc9457-minimal: errors");

  // <example id="rfc9457-problem" tags="rfc9457,json">
  // A validation failure as an RFC 9457 problem, written as JSON
  const fieldErrors = ErrorList(
    [
      Err.on("title", "", "must be 1-100 characters"),
      Err.on("listId", "x", "unknown list"),
    ],
    "Validation failed",
  );
  const body = ProblemConverter().convert(Invalid.INVALID_VALUE, fieldErrors);

  console.log(JSON.stringify(body, null, 2));
  // </example>
  check(body.status === 400, "rfc9457-problem: status");
  check(body.errors?.length === 2, "rfc9457-problem: errors");

  // <example id="codedetail-json" tags="codedetail,json">
  // A validation failure as a CodeDetail, written as JSON
  const codeFailures = ErrorList(
    [
      Err.on("title", "", "must be 1-100 characters"),
      Err.on("listId", "x", "unknown list"),
    ],
    "Validation failed",
  );
  const codeDetail = toCodeDetail(Invalid.INVALID_VALUE, codeFailures);

  console.log(JSON.stringify(codeDetail, null, 2));
  // </example>
  check(codeDetail.code === "kiit.dev:codes:Failed:Invalid:INVALID_VALUE", "codedetail-json: code");
  check(codeDetail.errors?.length === 2, "codedetail-json: errors");

  // <example id="rfc9457-domain-origin" tags="rfc9457,origin">
  // A custom code whose origin is a domain needs no registration: https://{origin}/docs/codes/{status}/{group}/{name}
  const domain = problems.convert(EMPTY_TITLE);
  console.log(domain.type); // https://samples.kiit.dev/docs/codes/failed/invalid/empty-title
  // </example>
  check(domain.type === "https://samples.kiit.dev/docs/codes/failed/invalid/empty-title", "rfc9457-domain-origin: type");

  // <example id="rfc9457-registered" tags="rfc9457,origin">
  // Register a base URL when the docs live somewhere else. An entry wins over the origin.
  const registered = ProblemConverter({ "samples.kiit.dev": "https://docs.samples.kiit.dev/errors" });
  console.log(registered.convert(EMPTY_TITLE).type); // https://docs.samples.kiit.dev/errors/failed/invalid/empty-title
  // </example>
  check(
    registered.convert(EMPTY_TITLE).type === "https://docs.samples.kiit.dev/errors/failed/invalid/empty-title",
    "rfc9457-registered: type",
  );

  // <example id="rfc9457-scope" tags="rfc9457">
  // Each dot in a scope starts a new path segment, before the status, group and name
  const scoped = problems.convert(DUPLICATE_TASK);
  console.log(scoped.type); // https://samples.kiit.dev/docs/codes/lists/team/failed/rejected/duplicate-task
  // </example>
  check(scoped.type === "https://samples.kiit.dev/docs/codes/lists/team/failed/rejected/duplicate-task", "rfc9457-scope: type");

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
  //   "status": 403,
  //   "code": "kiit.dev:codes:Failed:Restricted:FORBIDDEN"
  // }

  console.log(JSON.stringify(asDetail, null, 2));
  // {
  //   "code": "kiit.dev:codes:Failed:Restricted:FORBIDDEN",
  //   "success": false,
  //   "title": "Access to this resource is forbidden.",
  //   "status": 403
  // }
  // </example>
  check(asProblem.status === 403 && asDetail.status === 403, "rfc9457-vs-codedetail: same status");
  check(asDetail.code === "kiit.dev:codes:Failed:Restricted:FORBIDDEN", "rfc9457-vs-codedetail: code");

  // <example id="rfc9457-plain-origin" tags="rfc9457,origin">
  // A plain id isn't a domain, so the type is a relative path. Register a base URL or use a domain for an absolute URL.
  const plain = Rejected("OUT_OF_STOCK", "Out of stock", "myapp1");
  console.log(problems.convert(plain).type); // /docs/codes/failed/rejected/out-of-stock
  const fixed = ProblemConverter({ myapp1: "https://docs.example.com/myapp1/errors" });
  console.log(fixed.convert(plain).type); // https://docs.example.com/myapp1/errors/failed/rejected/out-of-stock
  // </example>
  check(problems.convert(plain).type === "/docs/codes/failed/rejected/out-of-stock", "rfc9457-plain-origin: relative");
  check(
    fixed.convert(plain).type === "https://docs.example.com/myapp1/errors/failed/rejected/out-of-stock",
    "rfc9457-plain-origin: registered",
  );

  // <example id="rfc9457-custom-type" tags="rfc9457,origin">
  // For your own type URLs there is no new API. Pick the way that fits how much of the URL you control.
  // 1. A typeBuilder builds the part after the base URL. Wrap it once so callers don't repeat it.
  const taskType = (status: Status): string => `tasks/${status.name.toLowerCase().replace(/_/g, "-")}`;
  const toProblem = (status: Status) => problems.convert(status, undefined, taskType);
  console.log(toProblem(EMPTY_TITLE).type); // https://samples.kiit.dev/docs/codes/tasks/empty-title

  // 2. convertWithUrl with a base and an empty suffix gives exactly that URL
  const exact = problems.convertWithUrl(EMPTY_TITLE, undefined, "https://example.com/probs/empty-title", () => "");
  console.log(exact.type); // https://example.com/probs/empty-title

  // 3. Problem is a plain object, so a spread sets any URL, even on another host
  const replaced = { ...problems.convert(EMPTY_TITLE), type: "https://other.example.org/probs/empty-title" };
  console.log(replaced.type); // https://other.example.org/probs/empty-title

  // code is built from the status, not from type, so it is the same in all three
  console.log(replaced.code); // samples.kiit.dev::Failed:Invalid:EMPTY_TITLE
  // </example>
  check(toProblem(EMPTY_TITLE).type === "https://samples.kiit.dev/docs/codes/tasks/empty-title", "rfc9457-custom-type: typeBuilder");
  check(exact.type === "https://example.com/probs/empty-title", "rfc9457-custom-type: empty suffix");
  check(replaced.type === "https://other.example.org/probs/empty-title", "rfc9457-custom-type: spread");
  check(
    toProblem(EMPTY_TITLE).code === exact.code && exact.code === replaced.code,
    "rfc9457-custom-type: code unchanged",
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
    stripeProblem.type === "https://stripe.com/errors/payments/cards/failed/restricted/duplicate-charge",
    "ProblemConverter.convert(...).type",
  );
  check(stripeProblem.status === 401, "ProblemConverter.convert(...).status");

  const detail = toCodeDetail(duplicateCharge);
  check(detail.code === "stripe.com:payments.cards:Failed:Restricted:DUPLICATE_CHARGE", "toCodeDetail(...).code");
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

/**
 * Guide: recipes. Each one stands alone, so the page can show it as it is.
 * The TypeScript port has no CodesToGrpc and no CompositeLookup yet, so those two recipes differ from Kotlin.
 */
function showGuide(): void {
  guideBuiltins();
  guideCustomCode();
  guidePatternMatching();
  guideCollectErrors();
  guideErrorDetails();
  guideExceptions();
  guideJson();
  guideTypeUrl();
  guideCustomTypeUrl();
  guideHttp();
  guideCustomProtocol();
}

function guideBuiltins(): void {
  section("Guide: Status: Built-ins");

  // <example id="guide-builtins" tags="guide">
  // A specific built-in code
  const created = Succeeded.CREATED;
  // The group's default, when you only know the kind of outcome
  const failed = Invalid.DEFAULT;
  // INVALID_VALUE
  console.log(failed.name);
  // true
  console.log(isDefault(failed));
  // false
  console.log(isDefault(Invalid.BAD_REQUEST));
  // </example>
  check(created.name === "CREATED", "guide-builtins: created");
  check(failed === Invalid.INVALID_VALUE && isDefault(failed), "guide-builtins: default");
  check(!isDefault(Invalid.BAD_REQUEST), "guide-builtins: not default");
}

function guideCustomCode(): void {
  section("Guide: Status: Custom code");

  // <example id="guide-custom-code" tags="guide">
  const PAYMENT_DECLINED = Rejected("PAYMENT_DECLINED", "Payment declined", "payments.example.com", "payments.cards");
  // </example>
  check(PAYMENT_DECLINED.group === Groups.REJECTED, "guide-custom-code: group");
  check(
    PAYMENT_DECLINED.origin === "payments.example.com" && PAYMENT_DECLINED.scope === "payments.cards",
    "guide-custom-code: origin and scope",
  );
}

function guidePatternMatching(): void {
  section("Guide: Status: Pattern matching");

  // <example id="guide-pattern-matching" tags="guide">
  // 1. Passed or Failed
  function binary(status: Status): string {
    if (status.success) {
      return `ok: ${status.name}`;
    }
    return `failed: ${status.name}`;
  }

  // 2. Each of the eight groups. The compiler flags a missing group.
  function nested(status: Status): string {
    switch (status.group) {
      case Groups.SUCCEEDED:
        return "done";
      case Groups.PENDING:
        return "in progress";
      case Groups.EXCLUDED:
        return "skipped";
      case Groups.INFORMATION:
        return "for your information";
      case Groups.RESTRICTED:
        return "not allowed";
      case Groups.INVALID:
        return "fix the input";
      case Groups.REJECTED:
        return "refused by a rule";
      case Groups.UNSERVED:
        return "try again later";
      default:
        return assertNever(status);
    }
  }

  // 3. Specific to broad: a code, then a group, then Failed or Passed
  function hybrid(status: Status): string {
    if (status.group === Groups.REJECTED && status.name === "CONFLICT") {
      return "already exists";
    }
    if (status.group === Groups.INVALID) {
      return "fix the input";
    }
    if (!status.success) {
      return `failed: ${status.name}`;
    }
    return `ok: ${status.name}`;
  }

  // failed: INVALID_VALUE
  console.log(binary(Invalid.INVALID_VALUE));
  // in progress
  console.log(nested(Pending.QUEUED));
  // already exists
  console.log(hybrid(Rejected.CONFLICT));
  // </example>
  check(binary(Invalid.INVALID_VALUE) === "failed: INVALID_VALUE" && binary(Succeeded.SUCCESS) === "ok: SUCCESS", "guide-pattern-matching: binary");
  check(nested(Succeeded.SUCCESS) === "done" && nested(Pending.QUEUED) === "in progress", "guide-pattern-matching: nested passed");
  check(nested(Excluded.SKIPPED) === "skipped" && nested(Information.NOTICE) === "for your information", "guide-pattern-matching: nested passed rest");
  check(nested(Invalid.INVALID_VALUE) === "fix the input" && nested(Unserved.TIMEOUT) === "try again later", "guide-pattern-matching: nested failed");
  check(nested(Restricted.DENIED) === "not allowed" && nested(Rejected.CONFLICT) === "refused by a rule", "guide-pattern-matching: nested failed rest");
  check(hybrid(Rejected.CONFLICT) === "already exists", "guide-pattern-matching: hybrid code first");
  check(hybrid(Invalid.INVALID_VALUE) === "fix the input", "guide-pattern-matching: hybrid group");
  check(hybrid(Unserved.TIMEOUT) === "failed: TIMEOUT" && hybrid(Succeeded.SUCCESS) === "ok: SUCCESS", "guide-pattern-matching: hybrid broad");
}

function guideCollectErrors(): void {
  section("Guide: Error Handling: Collect errors");

  // <example id="guide-collect-errors" tags="guide">
  function validateTitle(title: string): Checked {
    if (title.trim() !== "") {
      return Checked.success();
    }
    return Checked.failure(Invalid.INVALID_VALUE, [Err.on("title", title, "must not be blank")]);
  }

  function validateListId(listId: string): Checked {
    if (["personal", "team"].includes(listId)) {
      return Checked.success();
    }
    return Checked.failure(Invalid.NOT_FOUND, [Err.on("listId", listId, "unknown list")]);
  }

  const checked = collect(validateTitle(""), validateListId("unknown-list"));
  // valid = false, errors = 2
  console.log(`valid = ${checked.isValid}, errors = ${checked.errors.length}`);
  // </example>
  check(!checked.isValid, "guide-collect-errors: invalid");
  check(checked.errors.length === 2, "guide-collect-errors: two errors");
}

function guideErrorDetails(): void {
  section("Guide: Error Handling: Error details");

  // <example id="guide-error-details" tags="guide">
  // A plain message
  const plain = Err.of("title is required");
  // An error on one field, with its value
  const title = Err.on("title", "", "must be 1-100 characters");
  // The same, without the value
  const password = Err.onField("password", "must be at least 12 characters");
  // Several plain messages under one message
  const many = Err.list(["title is required", "list is unknown"], "Validation failed");
  // </example>
  check(plain.message === "title is required", "guide-error-details: plain");
  check(title.kind === "ErrorField" && title.field === "title", "guide-error-details: field");
  check(password.kind === "ErrorField" && password.field === "password", "guide-error-details: no value");
  check(many.errors.length === 2 && many.message === "Validation failed", "guide-error-details: list");
}

function guideExceptions(): void {
  section("Guide: Error Handling: Exceptions");

  // <example id="guide-exceptions" tags="guide">
  function create(title: string): Status {
    return title === "groceries" ? Rejected.CONFLICT : Succeeded.CREATED;
  }

  function createOrThrow(title: string): void {
    const status = create(title);
    if (!status.success) {
      throw toError(status);
    }
  }

  try {
    createOrThrow("groceries");
  } catch (e) {
    if (e instanceof RejectedError) {
      // CONFLICT
      console.log(e.status.name);
    }
  }
  // </example>
  let caught = "";
  try {
    createOrThrow("groceries");
  } catch (e) {
    if (e instanceof RejectedError) caught = e.status.name;
  }
  check(caught === "CONFLICT", "guide-exceptions: caught");
}

function guideJson(): void {
  section("Guide: Response: JSON");

  // <example id="guide-json" tags="guide">
  // A Problem and a CodeDetail are plain objects, so JSON.stringify works on them directly.
  // Listing the fields sets their order in the JSON.
  const errors = ErrorList([Err.on("title", "", "must be 1-100 characters")], "Validation failed");
  const problem = ProblemConverter().convert(Invalid.INVALID_VALUE, errors);
  const detail = toCodeDetail(Invalid.INVALID_VALUE, errors);

  // An RFC 9457 problem, for an HTTP API
  console.log(
    JSON.stringify(
      {
        type: problem.type,
        title: problem.title,
        detail: problem.detail,
        code: problem.code,
        status: problem.status,
        errors: problem.errors,
      },
      null,
      2,
    ),
  );
  // A CodeDetail, for your own services
  console.log(
    JSON.stringify(
      {
        code: detail.code,
        title: detail.title,
        detail: detail.detail,
        success: detail.success,
        errors: detail.errors,
      },
      null,
      2,
    ),
  );
  // </example>
  const problemText = JSON.stringify({ type: problem.type, title: problem.title, detail: problem.detail, code: problem.code, status: problem.status, errors: problem.errors }, null, 2);
  const detailText = JSON.stringify({ code: detail.code, title: detail.title, detail: detail.detail, success: detail.success, errors: detail.errors }, null, 2);
  const keys = (text: string) => text.split("\n").filter((l) => /^  "/.test(l)).map((l) => l.trim().split(":")[0]);
  check(keys(problemText).join() === '"type","title","detail","code","status","errors"', "guide-json: problem field order");
  check(keys(detailText).join() === '"code","title","detail","success","errors"', "guide-json: code detail field order");
}

function guideTypeUrl(): void {
  section("Guide: Response: Type URL");

  // <example id="guide-type-url" tags="guide">
  const stripe = Rejected("DUPLICATE_CHARGE", "Duplicate charge", "stripe.com", "payments.cards");
  const plain = Rejected("OUT_OF_STOCK", "Out of stock", "myapp1");

  // 1. A domain origin and nothing registered
  // https://stripe.com/docs/codes/payments/cards/failed/rejected/duplicate-charge
  console.log(ProblemConverter().convert(stripe).type);

  // 2. A plain id is not a domain, so the type is relative
  // /docs/codes/failed/rejected/out-of-stock
  console.log(ProblemConverter().convert(plain).type);

  // 3. A base URL registered for the origin
  // https://stripe.com/errors/payments/cards/failed/rejected/duplicate-charge
  const registered = ProblemConverter({ "stripe.com": "https://stripe.com/errors" });
  console.log(registered.convert(stripe).type);
  // </example>
  check(
    ProblemConverter().convert(stripe).type === "https://stripe.com/docs/codes/payments/cards/failed/rejected/duplicate-charge",
    "guide-type-url: domain",
  );
  check(ProblemConverter().convert(plain).type === "/docs/codes/failed/rejected/out-of-stock", "guide-type-url: plain id");
  check(
    registered.convert(stripe).type === "https://stripe.com/errors/payments/cards/failed/rejected/duplicate-charge",
    "guide-type-url: registered",
  );
}

function guideCustomTypeUrl(): void {
  section("Guide: Response: Custom type URL");

  // <example id="guide-custom-type-url" tags="guide">
  const stripe = Rejected("DUPLICATE_CHARGE", "Duplicate charge", "stripe.com", "payments.cards");
  const problems = ProblemConverter();

  // 1. Your own suffix, with a type builder
  // https://stripe.com/docs/codes/charges/duplicate
  console.log(problems.convert(stripe, undefined, () => "charges/duplicate").type);

  // 2. Exactly one URL, a base with an empty suffix
  // https://example.com/probs/duplicate-charge
  console.log(problems.convertWithUrl(stripe, undefined, "https://example.com/probs/duplicate-charge", () => "").type);

  // 3. Any URL at all, by copying the Problem
  // https://other.example.org/probs/duplicate
  console.log({ ...problems.convert(stripe), type: "https://other.example.org/probs/duplicate" }.type);
  // </example>
  check(problems.convert(stripe, undefined, () => "charges/duplicate").type === "https://stripe.com/docs/codes/charges/duplicate", "guide-custom-type-url: builder");
  check(
    problems.convertWithUrl(stripe, undefined, "https://example.com/probs/duplicate-charge", () => "").type === "https://example.com/probs/duplicate-charge",
    "guide-custom-type-url: exact",
  );
  check({ ...problems.convert(stripe), type: "https://other.example.org/probs/duplicate" }.type === "https://other.example.org/probs/duplicate", "guide-custom-type-url: copy");
  check(problems.convert(stripe).code === { ...problems.convert(stripe), type: "https://x.example/y" }.code, "guide-custom-type-url: code unchanged");
}

function guideHttp(): void {
  section("Guide: Response: HTTP");

  // <example id="guide-http" tags="guide">
  const http = CodesToHttp();

  // 201
  console.log(http.toCode(Succeeded.CREATED));
  // 409
  console.log(http.toCode(Rejected.CONFLICT));
  // </example>
  check(http.toCode(Succeeded.CREATED) === 201 && http.toCode(Rejected.CONFLICT) === 409, "guide-http: http");
}

function guideCustomProtocol(): void {
  section("Guide: Response: Custom protocol");

  // <example id="guide-custom-protocol" tags="guide">
  const PAYMENT_DECLINED = Rejected("PAYMENT_DECLINED", "Payment declined", "payments.example.com");

  // Your value first, the base mapping for everything else
  const base = CodesToHttp();
  const http: CodeLookup = {
    toCode: (status) => (status.origin === PAYMENT_DECLINED.origin && status.name === PAYMENT_DECLINED.name ? 402 : base.toCode(status)),
  };
  // 402
  console.log(http.toCode(PAYMENT_DECLINED));
  // 409
  console.log(http.toCode(Rejected.CONFLICT));
  // </example>
  check(http.toCode(PAYMENT_DECLINED) === 402, "guide-custom-protocol: extension");
  check(http.toCode(Rejected.CONFLICT) === 409, "guide-custom-protocol: falls back to the base");
}

showCoreFunctionality();
showExhaustivenessOverPassedAndFailed();
showExhaustivenessOverPassedGroups();
showExhaustivenessOverFailedGroups();
showErrors();
showChecked();
showFormats();
showGuide();

console.log(`\n${SEPARATOR}`);
console.log("All sample-ts checks passed.");
console.log(SEPARATOR);
