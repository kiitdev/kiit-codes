# Changelog

All notable changes to kiit-codes are documented here. Format follows
[Keep a Changelog](https://keepachangelog.com/), versions follow
[Semantic Versioning](https://semver.org/).

## [Unreleased]

| Area | Type | Impact | Description |
|---|---|---|---|
| `engines.node` | Updated | n/a | Lowered from `>=24` to `>=22.12`. TS |
| `exports` | Added | n/a | A `default` condition, so `require()` loads the ES module on Node 22.12 and later. TS |

## [1.2.0] - 2026-10-14

Items marked "TS" also apply to the TypeScript port.

| Area | Type | Impact | Description |
|---|---|---|---|
| `Status.DEFAULT`, `Status.isDefault` | Added | n/a | Each group has a `DEFAULT` alias (`Invalid.DEFAULT`), and `isDefault` tests for it. TS |
| `ProblemConverter` | Added | n/a | Builds `type` from the origin when no base URL is registered. `ProblemConverter()` needs no arguments. TS |
| `Problem.code` | Added | n/a | The `{origin}:{scope}:{Status.code}` string, same as `CodeDetail.code`. TS |
| `StatusRules.check` | Added | n/a | Returns the first problem with an origin, scope and name as a message, or `null`. Never throws. TS |
| `StatusException` | Added | n/a | Optional `message`, defaulting to the status title and separate from `errors`. Also on `toException` and the iOS `*Error` classes. TS |
| `StatusException.status` | Updated | Breaking | Typed to its group, so `RestrictedException.status` is a `Failed.Restricted`. `getStatus()` changes type. TS |
| `StatusException` constructors | Updated | Breaking | Order is now `(status, message, errors, cause)`, and `toException(message, errors)`. TS |
| Docs | Added | n/a | Subclassing an exception, and the `kiit.dev` origin being reserved for kiit codes |
| `Status` creation | Updated | Breaking | `origin`, `scope` and `name` are checked and throw `IllegalArgumentException` on bad values. TS |
| `Status.message`, `CodeDetail.message` | Updated | Breaking | Renamed `title`. `Err.message` is unchanged. TS |
| Built-in origin | Updated | Breaking | `"dev.kiit"` is now `"kiit.dev"`. |
| Built-in scope | Updated | Breaking | Every built-in has the scope `codes`, which changes `Status.path` and `CodeDetail.code`. TS |
| `CodeDetail` | Updated | Breaking | `path` is removed and `code` is `{origin}:{scope}:{Status.code}`. `errors` holds every error. TS |
| `Problem.type` | Updated | Breaking | Suffix is `{scope...}/{status}/{group}/{name}` in exact case. A built-in ends in `#codes`. TS |
| `CodesToProblem` | Updated | Breaking | Now `ProblemConverter(baseUrls)`. `build` is `convert`, `convert` is `convertWithUrl`. TS |
| `convertWithUrl` | Updated | n/a | Trims a trailing `/` from `baseUrl`. An unregistered origin no longer throws. |
| `CodeLookup.toStatus` | Removed | Breaking | Also on `CodesToHttp`, `CodesToGrpc` and `CompositeLookup`. `toCode` is unchanged. TS |
| `Catalog` | Removed | Breaking | Pass the map to `ProblemConverter(baseUrls = ...)`. TS also loses `CodesToProblem` and `problemFor`. |
| `Status.ofStatus` | Removed | Breaking | Use the status directly, or `copy()` it. `Err.ofStatus` is unchanged. TS |

### Migration

Each step applies only if you use that API. The TypeScript port follows the same steps.

1. **Converter**: replace `CodesToProblem` and `Catalog` with `ProblemConverter`, then rename the methods.
   Rename the old `convert` to `convertWithUrl` first, so the next rename does not collide with it.
   ```kotlin
   // Before
   CodesToProblem(Catalog.of("stripe.com" to "https://stripe.com/errors"), mapping).build(status, err)
   // After
   ProblemConverter(baseUrls = mapOf("stripe.com" to "https://stripe.com/errors")).convert(status, err)
   ```
   `convert` with an explicit `baseUrl` becomes `convertWithUrl`, `build` becomes `convert`, `buildCustom` becomes
   `convertCustom`, and the old `convertCustom` becomes `convertCustomWithUrl`. TS also replaces
   `problemFor(catalog, mapping, status, err)` with `ProblemConverter().convert(status, err)`.
2. **Title**: rename `message` to `title` on a custom `Status` (`title = "..."`), on `CodeDetail`, and in Java
   (`getMessage()` is `getTitle()`). `Err.message` stays.
3. **Origin**: update anything that stores or compares `"dev.kiit"` to `"kiit.dev"`. The Maven group is still `dev.kiit`.
4. **Code ids**: read `origin` and `scope` from the front of `CodeDetail.code` where you used `path`. A built-in is
   now `kiit.dev:codes:Failed:Restricted:FORBIDDEN`. A `CodesToHttp` or `CodesToGrpc` override key for a built-in
   needs `codes` in the scope slot (`origin:scope:group:name`).
5. **Stored `type`**: anything that saves or compares `Problem.type` needs the new form, for example
   `payments/cards/Failed/Rejected/DUPLICATE_CHARGE`, or `...?code=Failed:Invalid:INVALID_VALUE#codes` for a built-in.
6. **Validation**: `origin`, `scope` and `name` may only contain ASCII letters, digits, `.`, `-` and `_`. They start
   with a letter or digit, and `origin` and `name` cannot be empty. Fix values such as `host:8080` or `a/b`. Check a
   runtime value with `StatusRules.check(origin, scope, name)`. In Swift, constructors are `throws`, so calls need `try`.
7. **Removed**: replace `toStatus(code)` with `CodeDetail.code` or `Problem.code` to carry a status across a
   boundary. Replace `Status.ofStatus(...)` with the status itself, or `copy()` it.
8. **Exceptions**: pass `errors` by name, and rebuild code compiled against the old `getStatus()`.
   ```kotlin
   // Before
   InvalidException(status, errors)
   status.toException(errors)
   // After
   InvalidException(status, errors = errors)
   status.toException(errors = errors)
   ```
   The TS `StatusError` subclasses and `toError` change the same way. You can drop casts such as
   `status as Failed.Restricted`, since `status` now has that type.

## [1.1.0] - 2026-09-09

### Added
- RFC 9457 ("Problem Details for HTTP APIs") support: a new `kiit.codes.formats` package converts any `Status`
  into either the RFC 9457 shape (`CodesToProblem`) or kiit's own native equivalent (`CodeDetail`), with a
  `Catalog` for per-origin base URLs and support for custom error shapes.

### Changed
- **Breaking**: the built-in origin value changed from `"kiit"` to `"dev.kiit"` to match standard reverse-DNS
  naming. Anything comparing against the old value needs updating.

## [1.0.3] - 2026-09-02

### Added
- `HasStatus<S : Status>` capability interface, so a domain type can carry its own `Status`. `Checked` now
  implements it alongside `HasErrors`.

## [1.0.2] - 2026-08-23

### Fixed
- `CodesToHttp`/`CodesToGrpc` override maps and `Codes.statusFor` were keyed by origin+name only, which could
  collide across different groups sharing the same name. Both now key by origin+group+name instead.

### Changed
- **Breaking**: `Status.id` is no longer part of the public API (was `"$origin.$name"`, not unique enough).
  `Codes.statusFor(origin, name)` is now `Codes.statusFor(origin, group, name)`.

## [1.0.1] - 2026-08-15

### Fixed
- Excluded Group's `OMITTED`'s message corrected to stay neutral about cause, no longer implies the item was
  actively left out.

## [1.0.0] - 2026-08-14

### Changed
- Groups and codes finalized after a full design and audit pass.

## [0.x.x] - 2026-07-01

### Added
- Extracted from the Kiit framework as its own standalone module.
