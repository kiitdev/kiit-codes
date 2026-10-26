<div align="center">
<h1>
  <img src="https://raw.githubusercontent.com/kiitdev/kiit-codes/main/assets/kiit-codes-header.png" alt="@kiitdev/codes" align="center" />
</h1>

**General purpose status codes, for any layer of your app, to classify success and failure.**

A small, dependency-free status and error taxonomy for application outcomes, with extensible codes, HTTP mapping, validation, typed errors, and RFC 9457 support.

Part of [Kiit](https://www.kiit.dev) · [Docs](https://www.kiit.dev/docs/kiit-codes)

</div>

This is the native TypeScript port of [kiit-codes](https://github.com/kiitdev/kiit-codes). Kotlin is the canonical implementation. This package is a port checked against it, not an independent implementation that happens to agree today — see [`kiit-codes`](https://github.com/kiitdev/kiit-codes/tree/main/kiit-codes).

## Install

```bash
npm install @kiitdev/codes
```

## Requirements

Node 22.13 or later. The package is ESM-first with one build, and it works from both module systems:

```ts
import { Succeeded } from "@kiitdev/codes";
```

```js
const { Succeeded } = require("@kiitdev/codes");
```

`require()` loads the ES module directly, which Node supports without a flag or warning from 22.13. A project on Jest's default CommonJS setup may need `transformIgnorePatterns` changed to let Jest handle the package. Browser, Deno and Bun are not covered by this range.

## Start

A code is a name, a title, an origin that says who owns it, and an optional scope inside that origin.

```ts
import { Invalid } from "@kiitdev/codes";

const MISSING_DATE = Invalid("MISSING_DATE", "Date not supplied", "samples.kiit.dev", "tasks");
```

Codes expose stable fields suitable for application logic, logging, APIs, and diagnostics:

```json
{
    "group"   : "Rejected",
    "success" : false,
    "name"    : "CONFLICT",
    "title"   : "The request conflicts with the current state.",
    "origin"  : "kiit.dev",
    "scope"   : "codes"
}
```

`CodesToHttp` maps a status to an HTTP code, using group defaults plus specific overrides.

```ts
import { Succeeded, Invalid, CodesToHttp } from "@kiitdev/codes";

const http = CodesToHttp();

http.toCode(Succeeded.CREATED); // 201
http.toCode(Invalid.INVALID_VALUE); // 400
http.toCode(Invalid.NOT_FOUND); // 404
```

Every group is a plain object, not a class. Construct one without `new` (`Restricted.UNAUTHORIZED`, or `Restricted("CUSTOM_CODE", "Title of the code")` for a domain-specific one), and a value survives `JSON.parse` unchanged, since nothing about it depends on how it was constructed.

## Taxonomy

Codes uses a three-tier model:

| Tier   | Name       | Fixed/Open | Suggested wording                                                      |
| ------ | ---------- | ---------- | ---------------------------------------------------------------------- |
| Tier 1 | **Status** | Fixed      | Classifies an outcome as `Passed` or `Failed`.                         |
| Tier 2 | **Group**  | Fixed      | Classifies the kind of success or failure.                             |
| Tier 3 | **Code**   | Open       | Identifies a specific outcome using built-in or domain-specific codes. |

**Status → Group → Code**

`Status` and `Group` are fixed so applications share the same high-level meaning. `Code` is open: Kiit supplies common defaults while applications can add their own domain-specific codes.

![Kiit Codes taxonomy](https://raw.githubusercontent.com/kiitdev/kiit-codes/main/assets/kiit-codes-taxonomy.png)

The two statuses are:

- **Passed**: `Succeeded`, `Pending`, `Excluded`, `Information`
- **Failed**: `Restricted`, `Invalid`, `Rejected`, `Unserved`

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
//   type: "https://stripe.com/docs/codes/payments/cards/Failed/Rejected/DUPLICATE_CHARGE",
//   title: "This charge has already been processed",
//   status: 409,
//   detail: "Too low",
//   errors: [{ field: "amount", message: "Too low" }],
//   code: "stripe.com:payments.cards:Failed:Rejected:DUPLICATE_CHARGE"
// }
```

Four cases cover every `type`:

```ts
const duplicate = Rejected("DUPLICATE_CHARGE", "Duplicate charge", "stripe.com", "payments.cards");
const outOfStock = Rejected("OUT_OF_STOCK", "Out of stock", "myapp1");
```

| # | Case | `type` |
|---|---|---|
| 1 | Built-in | `https://www.kiit.dev/docs/kiit-codes?code=Failed:Invalid:INVALID_VALUE#codes` |
| 2 | Custom, no base URL, domain origin | `https://stripe.com/docs/codes/payments/cards/Failed/Rejected/DUPLICATE_CHARGE` |
| 3 | Custom, no base URL, plain id | `/docs/codes/Failed/Rejected/OUT_OF_STOCK` (relative) |
| 4 | Custom, with a base URL | `https://stripe.com/errors/payments/cards/Failed/Rejected/DUPLICATE_CHARGE` |

1. **type:** `{base}/{scope}/{status}/{group}/{name}`, in exact case. `status` is `Passed` or `Failed`, and each `.` in a scope starts a new segment. The last three segments are the same values as `statusCode(status)`, with `/` in place of `:`. The origin is not in the tail.
2. **Base:** a `baseUrls` entry for the origin, which is always absolute. With no entry, a domain origin gives `https://{origin}/docs/codes`, and any other id, such as `"myapp1"`, gives the relative `/docs/codes`, so register a base URL for a plain id.
3. **Built-ins:** the origin `kiit.dev` links to the Codes section of the kiit-codes docs, as `?code=Failed:Invalid:INVALID_VALUE#codes`, and an entry for `kiit.dev` is ignored. The origin is reserved for kiit-authored codes, so a custom status must not use it.
4. **Names:** an origin, a scope and a name can only contain letters, digits, `.`, `-` and `_`, and start with a letter or digit. A bad value throws when the status is created, and `StatusRules.check` tests one without throwing.
5. **Identity:** `type` is the problem's identity as well as its docs pointer, so pick the base once and keep it. `code` is the exact `{origin}:{scope}:{status code}`, the same string as `CodeDetail.code`. Read it for the origin, the scope and the status. The tail of `type` is not meant to be parsed.
6. **errors:** every error in the `Err` goes into `errors`. A list is expanded, and a single `Err.onField(...)` is one entry that keeps its field.
7. **Your own type URL:** pass a `typeBuilder` to `convert`, call `convertWithUrl` with a base and an empty suffix, or spread the problem with your own `type`: `{ ...problem, type: "https://..." }`. `code` does not change.

## Learn more

- [Full taxonomy and design docs](https://www.kiit.dev/docs/kiit-codes) (Kotlin-focused, same taxonomy)
- [Kotlin source](https://github.com/kiitdev/kiit-codes): the canonical implementation

## Contributing

Contributions and design feedback are welcome. See [BUILD.md](https://github.com/kiitdev/kiit-codes/blob/main/BUILD.md) for build, test, and publish instructions.

## License

[Apache License 2.0](./LICENSE)
