/**
 * Err is an error representation for use with Result/Outcome-style types, buildable from:
 * 1. simple strings
 * 2. exceptions
 * 3. a field with name/value
 * 4. a list of strings or Errs
 *
 * Like Status, this is a plain-object union (`ErrorInfo`/`ErrorField`/`ErrorList`) rather than a
 * class hierarchy, for the same reason: an `Err` received from `JSON.parse` should work exactly
 * like one built locally.
 */

import { Unserved } from "./groups.js";
import type { Status } from "./status.js";

/** Capability for any type that carries a list of `Err`. Implemented by `Checked`. */
export interface HasErrors {
  readonly errors: readonly Err[];
}

/** Capability for a domain type (success or error) that carries its own `Status`. */
export interface HasStatus<S extends Status = Status> {
  readonly status: S;
}

interface ErrFields {
  readonly message: string;
  readonly cause?: Error;
  readonly ref?: unknown;
}

/** Default `Err`: a message with an optional cause. */
export interface ErrorInfo extends ErrFields {
  readonly kind: "ErrorInfo";
}
export function ErrorInfo(message: string, cause?: Error, ref?: unknown): ErrorInfo {
  return { kind: "ErrorInfo", message, cause, ref };
}

/**
 * `Err` for an error on a specific field.
 * `field`: name of the field causing the error, e.g. "email".
 * `value`: value of the field causing the error, e.g. "some_invalid_value".
 */
export interface ErrorField extends ErrFields {
  readonly kind: "ErrorField";
  readonly field: string;
  readonly value: string;
}
export function ErrorField(
  field: string,
  value: string,
  message: string,
  cause?: Error,
  ref?: unknown,
): ErrorField {
  return { kind: "ErrorField", field, value, message, cause, ref };
}

/** `Err` that wraps a list of other errors. */
export interface ErrorList extends ErrFields {
  readonly kind: "ErrorList";
  readonly errors: readonly Err[];
}
export function ErrorList(errors: readonly Err[], message: string, cause?: Error, ref?: unknown): ErrorList {
  return { kind: "ErrorList", errors, message, cause, ref };
}

/** Any error representation. */
export type Err = ErrorInfo | ErrorField | ErrorList;

function isErr(value: unknown): value is Err {
  if (typeof value !== "object" || value === null || !("kind" in value)) return false;
  return value.kind === "ErrorInfo" || value.kind === "ErrorField" || value.kind === "ErrorList";
}

/** Convenience builders for `Err` from common sources: strings, exceptions, field errors. */
export const Err = {
  of(message: string, cause?: Error): Err {
    return ErrorInfo(message, cause);
  },

  /** Builds an `Err` directly from a `Status`, using its `message`. */
  ofStatus(status: Status): Err {
    return ErrorInfo(status.message);
  },

  on(field: string, value: string, message: string, cause?: Error): Err {
    return ErrorField(field, value, message, cause);
  },

  /**
   * Builds an `Err` for a `field` with a specific per-occurrence `message`, without a `value`.
   * Use this instead of `on` when the value itself could be sensitive (e.g. a password or
   * token) and shouldn't be carried on the error.
   */
  onField(field: string, message: string, cause?: Error): Err {
    return ErrorField(field, "", message, cause);
  },

  ex(cause: Error): Err {
    return ErrorInfo(cause.message || "", cause);
  },

  obj(err: unknown): Err {
    return ErrorInfo(String(err), undefined, err);
  },

  list(errors: readonly string[], message?: string): ErrorList {
    return ErrorList(
      errors.map((e) => ErrorInfo(e)),
      message ?? "Error occurred",
    );
  },

  build(error: unknown): Err {
    if (error === null || error === undefined) return Err.of(Unserved.UNEXPECTED.message);
    if (isErr(error)) return error;
    if (typeof error === "string") return Err.of(error);
    if (error instanceof Error) return Err.ex(error);
    return Err.obj(error);
  },
};
