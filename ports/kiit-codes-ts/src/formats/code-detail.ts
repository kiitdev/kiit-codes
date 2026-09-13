/**
 * kiit-native counterpart to `Problem`: `path`/`code`/`message` instead of RFC 9457's
 * `type`/`title`/`status`.
 *
 * {
 *     "path": "com.stripe:payments.cards",
 *     "code": "Failed:Rejected:DUPLICATE_CHARGE",
 *     "success": false,
 *     "message": "This charge has already been processed"
 * }
 *
 * 1. Meant for internal service-to-service calls.
 * 2. Also useful for background jobs and other non-API calls where an HTTP status isn't relevant.
 * 3. Self-contained, no need for a public URI: `origin:scope` is enough.
 * 4. `status` is optional - pass a `mapping` to `toCodeDetail` when this shape is still going out
 *    over HTTP and the status code is worth carrying alongside it.
 */

import { statusPath, statusCode } from "../status.js";
import type { Status } from "../status.js";
import type { Err } from "../err.js";
import type { CodeLookup } from "../codes.js";
import { type ErrorItem, type ErrorDetail, defaultErrorItem } from "./error-item.js";

export interface CodeDetail<T extends ErrorItem = ErrorItem> {
  readonly path: string;
  readonly code: string;
  readonly success: boolean;
  readonly message: string;
  readonly detail?: string;
  readonly instance?: string;
  readonly errors?: readonly T[];
  readonly status?: number;
}

/**
 * Builds a `CodeDetail<T>` for `status`, mapping each entry of an `ErrorList` through `mapper`.
 * Pass `mapping` to also populate `CodeDetail.status` with the equivalent HTTP status code.
 */
export function toCodeDetailCustom<T extends ErrorItem>(
  status: Status,
  err: Err | undefined,
  mapper: (err: Err) => T,
  mapping?: CodeLookup,
): CodeDetail<T> {
  const httpStatus = mapping?.toCode(status);
  if (err?.kind === "ErrorList") {
    return {
      path: statusPath(status),
      code: statusCode(status),
      success: status.success,
      message: status.message,
      detail: err.message,
      errors: err.errors.map(mapper),
      status: httpStatus,
    };
  }
  return {
    path: statusPath(status),
    code: statusCode(status),
    success: status.success,
    message: status.message,
    detail: err?.message,
    instance: err?.ref !== undefined ? String(err.ref) : undefined,
    status: httpStatus,
  };
}

/**
 * Builds the default `CodeDetail<ErrorDetail>` for `status` (and optionally `err`). Pass `mapping`
 * to also populate `CodeDetail.status` with the equivalent HTTP status code.
 */
export function toCodeDetail(status: Status, err?: Err, mapping?: CodeLookup): CodeDetail<ErrorDetail> {
  return toCodeDetailCustom(status, err, defaultErrorItem, mapping);
}
