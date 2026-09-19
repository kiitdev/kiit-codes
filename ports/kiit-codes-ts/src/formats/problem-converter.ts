/**
 * Converts a Status and an optional Err into an RFC 9457 `Problem`. Like `CodesToHttp`, it turns a
 * status into another representation.
 *
 * A factory function, not a class, matching `CodesToHttp`: `baseUrls`/`mapping` are closed over,
 * the `convert*` functions are local functions, not methods reading `this`.
 *
 * TERMS
 * 1. origin: `Status.origin`, the key of each `baseUrls` entry, e.g. `"stripe.com"`.
 * 2. baseUrl: text before the suffix, path prefix included, no trailing slash. e.g. `"https://stripe.com/errors"`
 * 3. suffix: `{scope}/{group}/{name}` in lowercase-dash, empty scope skipped.
 *    e.g. `"payments.cards/rejected/duplicate-charge"`
 * 4. type: the RFC 9457 `type` field, `{baseUrl}/{suffix}`.
 *
 * OPTIONAL
 * 1. `baseUrls` is optional.
 * 2. Without an entry, `https://{origin}/problems` is the baseUrl. The origin is lowercased and not validated.
 * 3. Add an entry when an origin's problem docs live elsewhere, or when it isn't a domain.
 * 4. An entry wins over the origin-derived baseUrl.
 *
 * EXAMPLE
 * 1. origin `"stripe.com"` -> baseUrl `"https://stripe.com/errors"`
 * 2. origin `"myapp1"` -> baseUrl `"https://docs.example.com/myapp1/errors"`
 *
 * ```ts
 * const converter = ProblemConverter({ "stripe.com": "https://stripe.com/errors" });
 * const problem = converter.convert(status, err);
 * ```
 *
 * With the first entry, a `Rejected` status named `DUPLICATE_CHARGE` with scope `payments.cards` converts to:
 * ```json
 * {
 *     "type": "https://stripe.com/errors/payments.cards/rejected/duplicate-charge",
 *     "title": "This charge has already been processed",
 *     "status": 409
 * }
 * ```
 *
 * With no entry, the same status with origin `"stripe.com"` gets the `type`
 * `https://stripe.com/problems/payments.cards/rejected/duplicate-charge`.
 *
 * NOTES
 * 1. Keys of `baseUrls` are lowercased and trailing `/` on a value is trimmed.
 * 2. `StatusConstants.KIIT` is always present and resolves to kiit-codes' own taxonomy docs.
 * 3. `StatusConstants.KIIT` can't be overridden. Its suffix is `?code=...#taxonomy`, joined with no `/`.
 * 4. Every `convert*` function ends in `convertCustomWithUrl`, the only place a `Problem` is assembled.
 * 5. `convertCustom`/`convertCustomWithUrl` take `mapper` before the defaulted `typeBuilder`, the opposite
 *    order from the Kotlin source: TypeScript requires defaulted parameters to come last, so `mapper`
 *    (no default, effectively required for the *Custom variants) has to sit before it.
 */

import type { Status } from "../status.js";
import type { Err } from "../err.js";
import type { CodeLookup } from "../codes.js";
import { CodesToHttp } from "../codes.js";
import { StatusConstants } from "../groups.js";
import { statusCode } from "../status.js";
import type { ErrorItem, ErrorDetail } from "./error-item.js";
import { defaultErrorItem } from "./error-item.js";
import type { Problem } from "./problem.js";

const KIIT_BASE_URL = "https://www.kiit.dev/docs/kiit-codes";

/**
 * Default `type` suffix appended to the baseUrl: `scope`/`group`/`name`, lowercase-dash. `origin` is
 * left out on purpose, the baseUrl is already specific to one origin, so repeating it would just
 * name that origin twice in the URL. An empty `scope` is skipped.
 *
 * ```ts
 * const status = Rejected("DUPLICATE_CHARGE", "Duplicate", "stripe.com", "payments.cards");
 * defaultTypeBuilder(status);   // "payments.cards/rejected/duplicate-charge"
 *
 * const noScope = Rejected("OUT_OF_STOCK", "Out of stock", "myapp1");
 * defaultTypeBuilder(noScope);  // "rejected/out-of-stock"
 * ```
 *
 * `StatusConstants.KIIT` is the exception: kiit-codes' own docs don't have a per-code anchor yet,
 * just the taxonomy page as a whole, so every kiit-origin status instead gets its `code` as a
 * query param on that same page, e.g. `?code=Failed:Invalid:INVALID_VALUE#taxonomy`.
 *
 * @param status the status to build the suffix for.
 * @returns the suffix, to be appended to a baseUrl. It is not a full URL.
 */
export function defaultTypeBuilder(status: Status): string {
  if (status.origin === StatusConstants.KIIT) return `?code=${statusCode(status)}#taxonomy`;

  const toUriSegment = (s: string): string => s.toLowerCase().replace(/_/g, "-");
  const segments = [status.scope, status.group, status.name].filter((s): s is string => s.length > 0);
  return segments.map(toUriSegment).join("/");
}

/**
 * @param baseUrls optional map of origin to baseUrl. Keys are lowercased, a trailing `/` on a value is trimmed.
 * @param mapping supplies the `status` code of each `Problem`, defaults to `CodesToHttp()`.
 */
export function ProblemConverter(
  baseUrls: Readonly<Record<string, string>> = {},
  mapping: CodeLookup = CodesToHttp(),
) {
  const registered = new Map<string, string>();
  for (const [origin, baseUrl] of Object.entries(baseUrls)) {
    registered.set(origin.toLowerCase(), baseUrl.replace(/\/+$/, ""));
  }
  registered.set(StatusConstants.KIIT, KIIT_BASE_URL);

  function baseUrlFor(status: Status): string {
    const origin = status.origin.toLowerCase();
    return registered.get(origin) ?? `https://${origin}/problems`;
  }

  /**
   * Same as `convertWithUrl`, but each `Err.ErrorList` entry goes through `mapper` into a `Problem<T>`.
   * This is the one function that assembles the `Problem`, every other `convert*` function ends here.
   *
   * @param status the status to convert.
   * @param err optional error, see `convert`.
   * @param baseUrl text before the suffix, path prefix included. A trailing `/` is trimmed.
   * @param mapper turns each `Err.ErrorList` entry into a `T`.
   * @param typeBuilder returns the `type` suffix, not a full URL. Defaults to `defaultTypeBuilder`.
   */
  function convertCustomWithUrl<T extends ErrorItem>(
    status: Status,
    err: Err | undefined,
    baseUrl: string,
    mapper: (err: Err) => T,
    typeBuilder: (status: Status) => string = defaultTypeBuilder,
  ): Problem<T> {
    const base = baseUrl.replace(/\/+$/, "");
    const path = typeBuilder(status);
    const joinedWithoutSlash = path.startsWith("?") || path.startsWith("#");
    const type = path.length === 0 ? base : joinedWithoutSlash ? `${base}${path}` : `${base}/${path}`;
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

  /**
   * Same as `convert`, but with an explicit `baseUrl`. `baseUrls` and the origin are not used.
   *
   * @param status the status to convert.
   * @param err optional error, see `convert`.
   * @param baseUrl text before the suffix, path prefix included. A trailing `/` is trimmed.
   * @param typeBuilder returns the `type` suffix, not a full URL. Defaults to `defaultTypeBuilder`.
   */
  function convertWithUrl(
    status: Status,
    err: Err | undefined,
    baseUrl: string,
    typeBuilder: (status: Status) => string = defaultTypeBuilder,
  ): Problem<ErrorDetail> {
    return convertCustomWithUrl(status, err, baseUrl, defaultErrorItem, typeBuilder);
  }

  /**
   * Same as `convert`, but each `Err.ErrorList` entry goes through `mapper` into a `Problem<T>`. Use this
   * for anything richer than field + message, see `ErrorItem`.
   *
   * @param status the status to convert.
   * @param err optional error, see `convert`.
   * @param mapper turns each `Err.ErrorList` entry into a `T`.
   * @param typeBuilder returns the `type` suffix, not a full URL. Defaults to `defaultTypeBuilder`.
   */
  function convertCustom<T extends ErrorItem>(
    status: Status,
    err: Err | undefined,
    mapper: (err: Err) => T,
    typeBuilder: (status: Status) => string = defaultTypeBuilder,
  ): Problem<T> {
    return convertCustomWithUrl(status, err, baseUrlFor(status), mapper, typeBuilder);
  }

  /**
   * Converts `status` and `err` into a `Problem<ErrorDetail>`. The baseUrl comes from `baseUrls`, or from
   * the origin when there is no entry.
   *
   * @param status the status to convert.
   * @param err optional error, an `Err.ErrorList` fills `errors`, any other fills `instance`. Both fill `detail`.
   * @param typeBuilder returns the `type` suffix, not a full URL. Defaults to `defaultTypeBuilder`.
   */
  function convert(
    status: Status,
    err?: Err,
    typeBuilder: (status: Status) => string = defaultTypeBuilder,
  ): Problem<ErrorDetail> {
    return convertCustom(status, err, defaultErrorItem, typeBuilder);
  }

  return { convert, convertCustom, convertWithUrl, convertCustomWithUrl };
}
