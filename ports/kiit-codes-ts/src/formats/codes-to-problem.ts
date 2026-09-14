/**
 * Converts a Status into an RFC 9457 `Problem`, the way `CodesToHttp`/`CodesToGrpc` convert one
 * into a protocol code. `catalog` supplies `baseUrl` per origin; `mapping` is reused rather than
 * built fresh per call.
 *
 * A factory function, not a class, matching `CodesToHttp` - `catalog`/`mapping` are closed over,
 * `build`/`buildCustom`/`convert`/`convertCustom` are local functions, not methods reading `this`.
 *
 * `buildCustom`/`convertCustom` take `mapper` before the defaulted `typeBuilder`, the opposite
 * order from the Kotlin source: TypeScript requires defaulted parameters to come last, so `mapper`
 * (no default, effectively required for the *Custom variants) has to sit before it.
 */

import type { Status } from "../status.js";
import type { Err } from "../err.js";
import type { CodeLookup } from "../codes.js";
import { StatusConstants } from "../groups.js";
import { statusCode } from "../status.js";
import type { ErrorItem, ErrorDetail } from "./error-item.js";
import { defaultErrorItem } from "./error-item.js";
import type { Catalog } from "./catalog.js";
import type { Problem } from "./problem.js";

/**
 * Default `type` suffix appended to `baseUrl`: `scope`/`group`/`name`, lowercase-dash. `origin` is
 * left out on purpose - `baseUrl` is already specific to one origin, so repeating it would just
 * name that origin twice in the URL.
 *
 * `StatusConstants.KIIT` is the exception: kiit-codes' own docs don't have a per-code anchor yet,
 * just the taxonomy page as a whole, so every kiit-origin status instead gets its `code` as a
 * query param on that same page, e.g. `?code=Failed:Invalid:INVALID_VALUE#taxonomy`.
 */
export function defaultTypeBuilder(status: Status): string {
  if (status.origin === StatusConstants.KIIT) return `?code=${statusCode(status)}#taxonomy`;

  const toUriSegment = (s: string): string => s.toLowerCase().replace(/_/g, "-");
  const segments = [status.scope, status.group, status.name].filter((s): s is string => s.length > 0);
  return segments.map(toUriSegment).join("/");
}

function toProblem<T extends ErrorItem>(
  status: Status,
  err: Err | undefined,
  baseUrl: string,
  typeBuilder: (status: Status) => string,
  mapper: (err: Err) => T,
  mapping: CodeLookup,
): Problem<T> {
  const path = typeBuilder(status);
  const type = path.length === 0 ? baseUrl : path.startsWith("?") || path.startsWith("#") ? `${baseUrl}${path}` : `${baseUrl}/${path}`;
  const code = mapping.toCode(status);

  if (err?.kind === "ErrorList") {
    return { type, title: status.message, status: code, detail: err.message, errors: err.errors.map(mapper) };
  }
  return {
    type,
    title: status.message,
    status: code,
    detail: err?.message,
    instance: err?.ref !== undefined ? String(err.ref) : undefined,
  };
}

export function CodesToProblem(catalog: Catalog, mapping: CodeLookup) {
  function buildCustom<T extends ErrorItem>(
    status: Status,
    err: Err | undefined,
    mapper: (err: Err) => T,
    typeBuilder: (status: Status) => string = defaultTypeBuilder,
  ): Problem<T> {
    const baseUrl = catalog[status.origin];
    if (baseUrl === undefined) {
      throw new Error(`No baseUrl registered for origin '${status.origin}'. Add one via Catalog.of().`);
    }
    return toProblem(status, err, baseUrl, typeBuilder, mapper, mapping);
  }

  /** Builds a `Problem<ErrorDetail>` for `status`, `baseUrl` looked up from `catalog`. */
  function build(
    status: Status,
    err?: Err,
    typeBuilder: (status: Status) => string = defaultTypeBuilder,
  ): Problem<ErrorDetail> {
    return buildCustom(status, err, defaultErrorItem, typeBuilder);
  }

  function convertCustom<T extends ErrorItem>(
    status: Status,
    err: Err | undefined,
    baseUrl: string,
    mapper: (err: Err) => T,
    typeBuilder: (status: Status) => string = defaultTypeBuilder,
  ): Problem<T> {
    return toProblem(status, err, baseUrl, typeBuilder, mapper, mapping);
  }

  /** Same as `build`, but `baseUrl` is supplied directly instead of looked up from `catalog`. */
  function convert(
    status: Status,
    err: Err | undefined,
    baseUrl: string,
    typeBuilder: (status: Status) => string = defaultTypeBuilder,
  ): Problem<ErrorDetail> {
    return convertCustom(status, err, baseUrl, defaultErrorItem, typeBuilder);
  }

  return { build, buildCustom, convert, convertCustom };
}

/** One-shot convenience: builds a `Problem<ErrorDetail>` without holding onto a `CodesToProblem`. */
export function problemFor(catalog: Catalog, mapping: CodeLookup, status: Status, err?: Err): Problem<ErrorDetail> {
  return CodesToProblem(catalog, mapping).build(status, err);
}
