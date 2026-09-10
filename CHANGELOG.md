# Changelog

All notable changes to kiit-codes are documented here. Format follows
[Keep a Changelog](https://keepachangelog.com/), versions follow
[Semantic Versioning](https://semver.org/).

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
