# Changelog

All notable changes to kiit-codes are documented here. Format follows
[Keep a Changelog](https://keepachangelog.com/), versions follow
[Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added
- `DEFAULT` on each group's companion (`Invalid.DEFAULT`, `Restricted.DEFAULT`, ...), an alias for that group's
  default code (`INVALID_VALUE`, `DENIED`, ...). It is the same instance, not a new registry entry.
- `Status.isDefault`, true when a status equals its group's `DEFAULT`. Compared by value, so a `copy()` that
  changes any field, `message` included, is not the default.
- TypeScript port: the same `DEFAULT` constants and a standalone `isDefault(status)` function.
- An origin can be a real domain. With no `baseUrls` entry, `ProblemConverter` builds the RFC 9457 `type` as
  `https://{origin}/problems/{scope}/{group}/{name}`. The origin is lowercased and not validated, so a plain id
  such as `"myapp1"` gives `https://myapp1/problems/...`. Register a base URL to change that.
- `ProblemConverter()` needs no arguments. Both `baseUrls` and `mapping` have defaults.
- TypeScript port: the same `ProblemConverter` changes. `ProblemConverter(baseUrls = {}, mapping = CodesToHttp())` is a
  factory function with the same `convert`, `convertCustom`, `convertWithUrl` and `convertCustomWithUrl` members.
  `mapper` comes before the defaulted `typeBuilder` in the two `*Custom*` members, as TypeScript requires.

### Changed
- **Breaking**: the built-in origin value changed from `"dev.kiit"` to `"kiit.dev"`. Anything comparing against or
  storing the old value needs updating. The Maven group ID is still `dev.kiit`.
- **Breaking**: `CodesToProblem` is now `ProblemConverter`, and it takes the origin-to-baseUrl map directly:
  `ProblemConverter(baseUrls = mapOf("stripe.com" to "https://stripe.com/errors"))`. This replaces
  `CodesToProblem(Catalog.of(...), mapping)`.
- **Breaking**: the methods are renamed, with the same parameters unless noted.
  - `build` is now `convert`, and `buildCustom` is now `convertCustom`.
  - The old `convert` (explicit `baseUrl`) is now `convertWithUrl`, and `convertCustom` is now
    `convertCustomWithUrl`.
- An origin with no registered base URL no longer throws `IllegalArgumentException`. It builds `type` from the
  origin instead.
- `convertWithUrl` and `convertCustomWithUrl` trim a trailing `/` from `baseUrl`, like `baseUrls` values.

### Removed
- **Breaking**: `Catalog`. Pass the map to `ProblemConverter(baseUrls = ...)` instead. Keys are lowercased and
  `kiit.dev` is always fixed to kiit-codes' own docs, as before.
- **Breaking**: the TypeScript port's `Catalog` and `CodesToProblem`, replaced by `ProblemConverter`. The one-shot
  `problemFor(catalog, mapping, status, err)` is now `problemFor(status, err?, baseUrls?, mapping?)`.
- **Breaking**: `Status.ofStatus(message, rawStatus, status)`. Overriding `message` produced a status with the same
  origin/scope/group/name as a built-in but not equal to it. Use the status directly, or `copy()` it if you need
  a different message. The TypeScript port's `ofStatus` is removed for the same reason. `Err.ofStatus(status)` is
  unaffected.

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
