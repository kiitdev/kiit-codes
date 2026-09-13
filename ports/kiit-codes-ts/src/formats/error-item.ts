/**
 * Minimal contract for one error-list entry, used by both `Problem` and `CodeDetail`. Only
 * `field` (what this is about) and `message` (what's wrong), matching the one thing gRPC's
 * `BadRequest.FieldViolation` and JSON:API's error objects agree on for a per-item shape. No
 * identifier, no doc-link, no timestamp: those belong on the parent `Problem`/`CodeDetail`, not
 * repeated per item.
 *
 * `Err`'s `cause` and `ErrorField.value` never appear here on purpose: a raw exception or the
 * value that failed validation are both real disclosure risks to echo back by default. `Err.ref`
 * is excluded too, but that's exactly what a custom `mapper: (err: Err) => T` reads to build a
 * richer type when a consumer decides more detail is safe for their own audience. `ErrorDetail` is
 * kiit-codes' own default `T`; supply your own type instead when you need more than this.
 */

import type { Err } from "../err.js";

export interface ErrorItem {
  readonly field?: string;
  readonly message: string;
}

/** Default `ErrorItem`: just `field` + `message`, see `ErrorItem` for why. */
export interface ErrorDetail extends ErrorItem {
  readonly field?: string;
  readonly message: string;
}
export function ErrorDetail(field: string | undefined, message: string): ErrorDetail {
  return { field, message };
}

/** Builds the default `ErrorDetail` from one `Err`, used by the non-generic converter overloads. */
export function defaultErrorItem(err: Err): ErrorDetail {
  return ErrorDetail(err.kind === "ErrorField" ? err.field : undefined, err.message);
}
