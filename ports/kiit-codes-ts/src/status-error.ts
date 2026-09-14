/**
 * Error hierarchy carrying a `Checked` instead of a plain message string.
 *
 * Throw one of the concrete classes (`RestrictedError`, `InvalidError`, `RejectedError`,
 * `UnservedError`) when a `Failed` status needs to cross a call boundary that can only
 * communicate via exceptions. `StatusError` itself is `abstract` and can't be thrown directly.
 *
 * Named `XxxError`, not `XxxException` — JavaScript's own convention (`Error`, `TypeError`,
 * `RangeError`) and the exact naming `jsMain/StatusError.kt` already uses for this reason. This
 * file supersedes that Kotlin/JS wrapper directly, name for name.
 *
 * Real classes here, unlike `Status`/`Err`/`Checked` elsewhere in this port: `throw`/`catch` is
 * inherently `instanceof`-based in JS, there's no plain-object idiom for it, and an exception is
 * a throw-time, in-process value that's never expected to survive a JSON round-trip the way
 * `Status` data is.
 *
 * ```ts
 * throw new RestrictedError(Restricted.UNAUTHORIZED);
 *
 * try {
 *   // ...
 * } catch (e) {
 *   if (e instanceof StatusError) {
 *     switch (e.status.group) {
 *       case Groups.RESTRICTED: // handle auth failure
 *       case Groups.INVALID:    // handle bad input
 *       case Groups.REJECTED:   // handle known business-rule failure
 *       case Groups.UNSERVED:   // handle capacity / timeout / unsupported / unexpected
 *     }
 *   }
 * }
 * ```
 *
 * Use `toError` to convert a bare `Failed` status into the matching subclass without writing
 * that `switch` yourself.
 */

import { assertNever } from "./assert-never.js";
import { Groups } from "./groups.js";
import type { Restricted, Invalid, Rejected, Unserved } from "./groups.js";
import type { Status, Failed } from "./status.js";
import { Checked } from "./checked.js";
import { Err } from "./err.js";

export abstract class StatusError extends Error {
  readonly checked: Checked;

  constructor(checked: Checked, options?: ErrorOptions) {
    super(checked.status.message, options);
    this.checked = checked;
    this.name = new.target.name;
  }

  get status(): Status {
    return this.checked.status;
  }

  get errors(): readonly Err[] {
    return this.checked.errors;
  }
}

/** Thrown for a `Failed.Restricted` status: a security or access-control failure. */
export class RestrictedError extends StatusError {
  constructor(status: Restricted, errors: readonly Err[] = [], options?: ErrorOptions) {
    super(Checked.failure(status, errors.length > 0 ? errors : [Err.ofStatus(status)]), options);
  }
}

/** Thrown for a `Failed.Invalid` status: the request as given cannot be satisfied. */
export class InvalidError extends StatusError {
  constructor(status: Invalid, errors: readonly Err[] = [], options?: ErrorOptions) {
    super(Checked.failure(status, errors.length > 0 ? errors : [Err.ofStatus(status)]), options);
  }
}

/** Thrown for a `Failed.Rejected` status: a known, expected business-rule failure. */
export class RejectedError extends StatusError {
  constructor(status: Rejected, errors: readonly Err[] = [], options?: ErrorOptions) {
    super(Checked.failure(status, errors.length > 0 ? errors : [Err.ofStatus(status)]), options);
  }
}

/** Thrown for a `Failed.Unserved` status: valid and permitted, but can't be serviced right now. */
export class UnservedError extends StatusError {
  constructor(status: Unserved, errors: readonly Err[] = [], options?: ErrorOptions) {
    super(Checked.failure(status, errors.length > 0 ? errors : [Err.ofStatus(status)]), options);
  }
}

/**
 * Converts a bare `Failed` status into the matching `StatusError` subclass, so callers don't need
 * to write the `switch` themselves.
 *
 * Exhaustive over `Failed`'s four groups via `assertNever`: if `Failed` ever gains a new group,
 * this becomes a compile error to fix here, not something a default case would silently mishandle.
 */
export function toError(failed: Failed, errors: readonly Err[] = []): StatusError {
  switch (failed.group) {
    case Groups.RESTRICTED:
      return new RestrictedError(failed, errors);
    case Groups.INVALID:
      return new InvalidError(failed, errors);
    case Groups.REJECTED:
      return new RejectedError(failed, errors);
    case Groups.UNSERVED:
      return new UnservedError(failed, errors);
    default:
      return assertNever(failed);
  }
}
