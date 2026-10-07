/**
 * kiit-native counterpart to `Problem`: `code` instead of RFC 9457's `type`, and `status` is optional.
 *
 * {
 *     "code": "stripe.com:payments.cards:Failed:Rejected:DUPLICATE_CHARGE",
 *     "success": false,
 *     "title": "This charge has already been processed"
 * }
 *
 * 1. Meant for internal service-to-service calls.
 * 2. Also useful for background jobs and other non-API calls where an HTTP status isn't relevant.
 * 3. Self-contained, no need for a public URI: `code` is `origin`, `scope` and `statusCode`, so it
 *    names the origin as well as the status. An empty scope leaves an empty value
 *    (`myapp1::Failed:Rejected:OUT_OF_STOCK`), so `code` always has five `:`-separated values.
 * 4. `status` is optional - pass a `mapping` to `toCodeDetail` when this shape is still going out
 *    over HTTP and the status code is worth carrying alongside it.
 */

import type { Status } from "../status.js";
import type { Err } from "../err.js";
import type { CodeLookup } from "../codes.js";
import { type ErrorItem, type ErrorDetail, defaultErrorItem } from "./error-item.js";
import { detailCode, errParts } from "./internal.js";

export interface CodeDetail<T extends ErrorItem = ErrorItem> {
  readonly code: string;
  readonly success: boolean;
  readonly title: string;
  readonly detail?: string;
  readonly instance?: string;
  readonly errors?: readonly T[];
  readonly status?: number;
}

/**
 * Builds a `CodeDetail<T>` for `status`, mapping every error in `err` through `mapper`. An `ErrorList` is expanded,
 * recursively, and any other `Err` is one entry. `detail` is the error's message, or the first non-blank error
 * message when that is blank. Pass `mapping` to also populate `CodeDetail.status` with the equivalent HTTP status code.
 */
export function toCodeDetailCustom<T extends ErrorItem>(
  status: Status,
  err: Err | undefined,
  mapper: (err: Err) => T,
  mapping?: CodeLookup,
): CodeDetail<T> {
  const parts = errParts(err, mapper);
  return {
    code: detailCode(status),
    success: status.success,
    title: status.title,
    detail: parts.detail,
    instance: parts.instance,
    errors: parts.errors,
    status: mapping?.toCode(status),
  };
}

/**
 * Builds the default `CodeDetail<ErrorDetail>` for `status` (and optionally `err`). Pass `mapping`
 * to also populate `CodeDetail.status` with the equivalent HTTP status code.
 */
export function toCodeDetail(status: Status, err?: Err, mapping?: CodeLookup): CodeDetail<ErrorDetail> {
  return toCodeDetailCustom(status, err, defaultErrorItem, mapping);
}
