/**
 * Shared by `toCodeDetail` and `ProblemConverter`. Not exported from the package.
 */

import { statusCode } from "../status.js";
import type { Status } from "../status.js";
import type { Err } from "../err.js";
import type { ErrorItem } from "./error-item.js";

/** The exact identity of a status, `{origin}:{scope}:{statusCode}`. Shared by `CodeDetail.code` and `Problem.code`. */
export function detailCode(status: Status): string {
  return `${status.origin}:${status.scope}:${statusCode(status)}`;
}

/**
 * Flattens an `Err` into its leaf errors: an `ErrorList` is expanded, recursively, and any other `Err` is one entry.
 * So an `ErrorField` from `Err.onField("email", "Missing")` is one entry that keeps its field, and a list nested
 * inside a list does not collapse into a single entry.
 */
export function leaves(err: Err): readonly Err[] {
  return err.kind === "ErrorList" ? err.errors.flatMap(leaves) : [err];
}

/** The `detail`, `instance` and `errors` of a `Problem` or `CodeDetail`, built from one optional `Err`. */
export interface ErrParts<T extends ErrorItem> {
  readonly detail?: string;
  readonly instance?: string;
  readonly errors?: readonly T[];
}

/**
 * Splits an optional `Err` into the parts the converters share.
 *
 * 1. `errors`: every leaf of the error through `mapper`, or `undefined` when there is no leaf.
 * 2. `detail`: the error's own message when it isn't blank, otherwise the first leaf message that isn't blank.
 * 3. `instance`: `ref` of a single error as text. An `ErrorList` has none.
 * 4. No error gives all three as `undefined`.
 */
export function errParts<T extends ErrorItem>(err: Err | undefined, mapper: (err: Err) => T): ErrParts<T> {
  if (err === undefined) return {};
  const items = leaves(err);
  const detail = [err.message, ...items.map((e) => e.message)].find((m) => m.trim().length > 0);
  return {
    detail,
    instance: err.kind !== "ErrorList" && err.ref != null ? String(err.ref) : undefined,
    errors: items.length === 0 ? undefined : items.map(mapper),
  };
}
