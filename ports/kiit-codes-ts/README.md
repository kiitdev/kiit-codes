# @kiit/codes

A native TypeScript port of [kiit-codes](https://github.com/kiitdev/kiit-codes). A small, dependency-free taxonomy for classifying success and failure: extensible codes, HTTP mapping, validation, typed errors, and RFC 9457 support.

Kotlin is the canonical implementation. This package is a port checked against it, not an independent implementation that happens to agree today — see [`kiit-codes-kotlin`](https://github.com/kiitdev/kiit-codes/tree/main/kiit-codes-kotlin).

Pre-1.0: the API may still shift before a stable release.

## Install

```bash
npm install @kiit/codes
```

## Quick example

```ts
import { Succeeded, Restricted, CodesToHttp } from "@kiit/codes";

function authorize(userId: string, requesterId: string) {
  return userId === requesterId ? Succeeded.SUCCESS : Restricted.UNAUTHORIZED;
}

const status = authorize("alice", "bob");
const http = CodesToHttp();
console.log(http.toCode(status)); // 401
```

Every group is a plain object, not a class. Construct one without `new` (`Restricted.UNAUTHORIZED`, or `Restricted("CUSTOM_CODE", "message")` for a domain-specific one), and a value survives `JSON.parse` unchanged, since nothing about it depends on how it was constructed.

## Exhaustive narrowing

```ts
import { Groups, assertNever } from "@kiit/codes";
import type { Status } from "@kiit/codes";

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

## Learn more

- [Full taxonomy and design docs](https://www.kiit.dev/docs/kiit-codes) (Kotlin-focused, same taxonomy)
- [Kotlin source](https://github.com/kiitdev/kiit-codes): the canonical implementation

## License

[Apache License 2.0](./LICENSE)
