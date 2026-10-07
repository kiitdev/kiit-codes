# @kiitdev/codes

A native TypeScript port of [kiit-codes](https://github.com/kiitdev/kiit-codes). A small, dependency-free taxonomy for classifying success and failure: extensible codes, HTTP mapping, validation, typed errors, and RFC 9457 support.

Kotlin is the canonical implementation. This package is a port checked against it, not an independent implementation that happens to agree today — see [`kiit-codes`](https://github.com/kiitdev/kiit-codes/tree/main/kiit-codes).

Pre-1.0: the API may still shift before a stable release.

## Install

```bash
npm install @kiitdev/codes
```

## Quick example

```ts
import { Succeeded, Restricted, CodesToHttp } from "@kiitdev/codes";

function authorize(userId: string, requesterId: string) {
  return userId === requesterId ? Succeeded.SUCCESS : Restricted.UNAUTHORIZED;
}

const status = authorize("alice", "bob");
const http = CodesToHttp();
console.log(http.toCode(status)); // 401
```

Every group is a plain object, not a class. Construct one without `new` (`Restricted.UNAUTHORIZED`, or `Restricted("CUSTOM_CODE", "Title of the code")` for a domain-specific one), and a value survives `JSON.parse` unchanged, since nothing about it depends on how it was constructed.

## Exhaustive narrowing

```ts
import { Groups, assertNever } from "@kiitdev/codes";
import type { Status } from "@kiitdev/codes";

function describe(status: Status): string {
  switch (status.group) {
    case Groups.SUCCEEDED: return "ok";
    case Groups.PENDING: return "waiting";
    case Groups.EXCLUDED: return "excluded";
    case Groups.INFORMATION: return "info";
    case Groups.RESTRICTED: return "restricted";
    case Groups.INVALID: return "invalid";
    case Groups.REJECTED: return "rejected";
    case Groups.UNSERVED: return "unserved";
    default: return assertNever(status); // dropping a case here is a compile error
  }
}
```

## Problem details (RFC 9457)

`ProblemConverter` turns a status and an optional `Err` into an [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457.html) problem for an HTTP response. `toCodeDetail` is the kiit-native shape for service-to-service calls.

```ts
import { ProblemConverter, Rejected, Err } from "@kiitdev/codes";

const status = Rejected("DUPLICATE_CHARGE", "This charge has already been processed", "stripe.com", "payments.cards");
const problem = ProblemConverter().convert(status, Err.onField("amount", "Too low"));
// {
//   type: "https://stripe.com/docs/codes/payments/cards/failed/rejected/duplicate-charge",
//   title: "This charge has already been processed",
//   status: 409,
//   detail: "Too low",
//   errors: [{ field: "amount", message: "Too low" }],
//   code: "stripe.com:payments.cards:Failed:Rejected:DUPLICATE_CHARGE"
// }
```

1. **type:** `{base}/{scope…}/{status}/{group}/{name}`, lowercase with dashes, and each `.` in a scope starts a new segment. A domain origin gives `https://{origin}/docs/codes/...`. Any other origin, such as `"myapp1"`, gives the relative `/docs/codes/...`, so register a base URL to get an absolute one: `ProblemConverter({ myapp1: "https://docs.example.com/myapp1/errors" })`.
2. **Identity:** `type` is the problem's identity as well as its docs pointer, so pick the base once and keep it. `code` is the exact `{origin}:{scope}:{status code}`, the same string as `CodeDetail.code`. Read it instead of parsing `type`.
3. **errors:** every error in the `Err` goes into `errors`. A list is expanded, and a single `Err.onField(...)` is one entry that keeps its field.
4. **Your own type URL:** pass a `typeBuilder` to `convert`, call `convertWithUrl` with a base and an empty suffix, or spread the problem with your own `type`: `{ ...problem, type: "https://..." }`. `code` does not change.

## Publishing

Run from the repo root (or use `../../scripts/publish-npm.sh` if you're already in this directory):

```bash
npm login          # one-time; verify with `npm whoami`
./scripts/publish-npm.sh
```

The script runs typecheck/test/build, previews the package contents (`npm pack --dry-run`), asks for confirmation, then publishes. See [`BUILD.md`](../../BUILD.md#publish--npm-kiitdevcodes) for the CI-driven release path.

## Learn more

- [Full taxonomy and design docs](https://www.kiit.dev/docs/kiit-codes) (Kotlin-focused, same taxonomy)
- [Kotlin source](https://github.com/kiitdev/kiit-codes): the canonical implementation

## License

[Apache License 2.0](./LICENSE)
