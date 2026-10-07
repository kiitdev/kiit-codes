# Changelog

All notable changes to kiit-codes are documented here. Format follows
[Keep a Changelog](https://keepachangelog.com/), versions follow
[Semantic Versioning](https://semver.org/).

## [Unreleased]

### Added
- `DEFAULT` on each group's companion (`Invalid.DEFAULT`, `Restricted.DEFAULT`, ...), an alias for that group's
  default code (`INVALID_VALUE`, `DENIED`, ...). It is the same instance, not a new registry entry.
- `Status.isDefault`, true when a status equals its group's `DEFAULT`. Compared by value, so a `copy()` that
  changes any field, `title` included, is not the default.
- TypeScript port: the same `DEFAULT` constants and a standalone `isDefault(status)` function.
- An origin can be a real domain. With no `baseUrls` entry, `ProblemConverter` builds the RFC 9457 `type` as
  `{base}/docs/codes/{scope...}/{status}/{group}/{name}`, lowercase with dashes. `status` is `passed` or `failed`, and
  each `.` in a scope starts a new segment. A domain origin (two or more dot-separated labels of letters, digits and
  hyphens) gives an absolute `https://{origin}/docs/codes/...`. Any other id, such as `"myapp1"`, gives the relative
  `/docs/codes/...`, since it is not a host. RFC 9457 prefers absolute URIs, so register a base URL to get one. An
  entry always wins and is always absolute, and only the suffix is built for it. `type` is the problem's identity, so
  pick the base once and keep it, and do not let two statuses produce the same `type`. The default suffix is
  `defaultTypeBuilder`. For your own URL, pass a `typeBuilder`, call `convertWithUrl` with a base and an empty
  suffix, or replace the field with `copy(type = ...)`.
- `Problem.code`, the exact `{origin}:{scope}:{Status.code}` string, the same value as `CodeDetail.code`. It is a kiit
  extension member, always set by `ProblemConverter`, and `null` by default for a `Problem` built by hand. Read it
  instead of parsing the lowercase `type`. The TypeScript port builds the same `type` and has the same optional `code`.
- `ProblemConverter()` needs no arguments. Both `baseUrls` and `mapping` have defaults.
- TypeScript port: the same `ProblemConverter` changes. `ProblemConverter(baseUrls = {}, mapping = CodesToHttp())` is a
  factory function with the same `convert`, `convertCustom`, `convertWithUrl` and `convertCustomWithUrl` members.
  `mapper` comes before the defaulted `typeBuilder` in the two `*Custom*` members, as TypeScript requires.

### Changed
- **Breaking**: `Status.message` is now `title`, on every status. It is the fixed text of a code, and `Err.message`
  stays the text of one occurrence. Named `message =` arguments in a custom status become `title =`, and Java
  `getMessage()` becomes `getTitle()`. `Problem.title` and `CodeDetail.title` are filled from it. The TypeScript
  port's `Status` has `title` too.
- **Breaking**: `CodeDetail.message` is now `title`, matching `Problem.title`. The TypeScript port's `CodeDetail` too.
- `toCodeDetail` and `ProblemConverter` put every error in `errors`. A single `Err.ErrorField` is one entry that keeps
  its field, a plain `Err.ErrorInfo` is one entry with no field, and an `Err.ErrorList` is expanded, including lists
  nested in lists. Before, only an `Err.ErrorList` filled `errors`, and a single error left it `null` and lost its
  field. `detail` is the error's message, or the first non-blank error message when that is blank. The TypeScript
  port does the same.
- **Breaking**: the built-in origin value changed from `"dev.kiit"` to `"kiit.dev"`. Anything comparing against or
  storing the old value needs updating. The Maven group ID is still `dev.kiit`.
- **Breaking**: `CodeDetail` has one identifier instead of two. `path` is removed, and `code` is now
  `"{origin}:{scope}:{Status.code}"`, for example `stripe.com:payments.cards:Failed:Rejected:DUPLICATE_CHARGE`. An
  empty scope stays as an empty slot (`kiit.dev::Failed:Restricted:FORBIDDEN`), so `code` always has five
  `:`-separated values, unlike `Status.path`, which skips it. `Status.path` and `Status.code` are unchanged, and
  `Status.code` is the suffix of `CodeDetail.code`. Read `origin` and `scope` off the front of `code` where you used
  `path`. The TypeScript port's `CodeDetail` changes the same way.
- **Breaking**: every built-in status now has the scope `codes` (`StatusConstants.CODES`, `StatusConstants.CODES` in
  the TypeScript port). `Status.path` of a built-in is `kiit.dev:codes` instead of `kiit.dev`, and
  `CodeDetail.code` is `kiit.dev:codes:Failed:Restricted:FORBIDDEN`. A built-in is no longer equal to the same
  status with an empty scope, so a custom status built with `origin = StatusConstants.KIIT` and no scope is no
  longer a built-in. `CodesToHttp` and `CodesToGrpc` overrides are keyed by `origin:scope:group:name`, so a
  hand-written key for a built-in needs `codes` in the scope slot. `Codes.statusFor(origin, group, name)` finds
  built-ins by the same arguments. `Problem.type` of a built-in is unchanged, since it never uses the scope.
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
- **Breaking**: `CodeLookup.toStatus(code)`, and with it `CodesToHttp.toStatus`, `CodesToGrpc.toStatus` and
  `CompositeLookup.toStatus`. Many statuses share one protocol code, so the reverse lookup was lossy and relied on
  a hand-kept preference list to pick a winner. `toCode` is unchanged. To carry a status across a boundary, use
  `CodeDetail.code` or `Problem.code`. The TypeScript port's `toStatus` is removed too.
- **Breaking**: `Catalog`. Pass the map to `ProblemConverter(baseUrls = ...)` instead. Keys are lowercased and
  `kiit.dev` is always fixed to kiit-codes' own docs, as before.
- **Breaking**: the TypeScript port's `Catalog` and `CodesToProblem`, replaced by `ProblemConverter`.
- **Breaking**: the TypeScript port's one-shot `problemFor(catalog, mapping, status, err)`. Use
  `ProblemConverter().convert(status, err)`. Kotlin never had an equivalent.
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
