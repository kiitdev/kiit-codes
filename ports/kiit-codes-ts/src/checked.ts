/**
 * Non-monadic result of a validation-style check that reports every problem found rather than
 * short-circuiting on the first one.
 *
 * 1. A form with three invalid fields should report all three, not just the first. Deliberately
 *    has no `map`/`flatMap`. Compose multiple checks with `collect` instead of chaining.
 * 2. Construction is only through `Checked.success`/`Checked.failure`, so `status` and `errors`
 *    can never be out of sync: a passing `Checked` always has an empty `errors` array, a failing
 *    one always has at least one entry. There's no exported bare constructor.
 * 3. `status` is typed `Status` rather than narrowed to `Passed`/`Failed`, since one `Checked`
 *    instance can represent either outcome.
 */

import { Succeeded, Invalid } from "./groups.js";
import type { Status, Passed, Failed } from "./status.js";
import type { Err, HasErrors, HasStatus } from "./err.js";

export interface Checked extends HasErrors, HasStatus<Status> {
  readonly isValid: boolean;
}

export const Checked = {
  /** A passing check with no errors. */
  success(status: Passed = Succeeded.SUCCESS): Checked {
    return { status, errors: [], isValid: true };
  },

  /** A failing check with one or more `errors`. */
  failure(status: Failed, errors: readonly Err[]): Checked {
    if (errors.length === 0) {
      throw new Error("failure requires at least one Err");
    }
    return { status, errors, isValid: false };
  },
};

/**
 * Collects multiple `checks` into one: passes only if every one of them passed, otherwise fails
 * with `Invalid.INVALID_VALUE` and every error from every failing entry pooled together, in the
 * order the checks were given.
 *
 * A real rest parameter, unlike Kotlin's compiled JS output, which exports vararg overloads as
 * plain array parameters, not true varargs (one of the original reasons for this port). One
 * function covers both `collect(a, b, c)` and `collect(...checksArray)`, no separate list overload
 * needed.
 */
export function collect(...checks: readonly Checked[]): Checked {
  const errors = checks.flatMap((c) => c.errors);
  return errors.length === 0 ? Checked.success() : Checked.failure(Invalid.INVALID_VALUE, errors);
}
