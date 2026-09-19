/**
 * Platform-agnostic status type describing the outcome of any operation: a service call, a
 * background job step, an API request, or a CLI command.
 *
 * Shape (maps directly to JSON / API error responses):
 *
 * {
 *     "name"    : "DENIED",
 *     "group"   : "Restricted",
 *     "origin"  : "dev.kiit",
 *     "scope"   : "",
 *     "message" : "The request was denied.",
 *     "success" : false
 * }
 *
 * Groups are fixed by design, to keep the taxonomy consistent across every consumer. Individual
 * codes within a group are open: create new domain codes by calling a group's constructor
 * directly (see groups.ts for the eight shapes and their built-in codes).
 *
 *   Status  = Passed     | Failed
 *   Passed  = Succeeded  | Pending | Excluded | Information
 *   Failed  = Restricted | Invalid | Rejected | Unserved
 *
 * Design notes:
 * 1. `groupDescription`/`statusPath`/`statusCode` below are standalone functions, not methods. A
 *    `Status` received from `JSON.parse` (an API response, a queue payload) is structurally
 *    identical to one built locally, so all three work on it unchanged. A class-based design
 *    loses `instanceof` and any instance method the moment a value crosses that boundary, since
 *    `JSON.parse` never runs a constructor.
 * 2. `Status` is a real union of the eight shapes in groups.ts, so `switch (status.group)` narrows
 *    the whole object, and `assertNever` in a `default` branch is a genuine compile-time
 *    exhaustiveness check.
 */

import { assertNever } from "./assert-never.js";
import { Groups, Succeeded, Pending, Excluded, Information, Restricted, Invalid, Rejected, Unserved } from "./groups.js";

/** Every non-failure status. */
export type Passed = Succeeded | Pending | Excluded | Information;

/** Every failure status. */
export type Failed = Restricted | Invalid | Rejected | Unserved;

/** Any status, success or failure. */
export type Status = Passed | Failed;

/** This group's plain-language description. Exhaustive over every group. */
export function groupDescription(status: Status): string {
  switch (status.group) {
    case Groups.SUCCEEDED:
      return "The operation completed successfully.";
    case Groups.PENDING:
      return "The operation was accepted but has not yet fully resolved.";
    case Groups.EXCLUDED:
      return "The item was intentionally excluded from the operation.";
    case Groups.INFORMATION:
      return "The response provides information; no operation was performed.";
    case Groups.RESTRICTED:
      return "The caller is not allowed.";
    case Groups.INVALID:
      return "The request itself is wrong.";
    case Groups.REJECTED:
      return "The caller was allowed, but the business refuses it.";
    case Groups.UNSERVED:
      return "The system can't serve it right now, though nothing was wrong with the request.";
    default:
      return assertNever(status);
  }
}

/**
 * Public, display-oriented, `:`-delimited identity for logging and debugging. Not for lookups or
 * comparison, since `scope` is consumer-defined and can change over time. See `statusCode` for a
 * value that's safe to compare across releases.
 *
 * Examples:
 * 1. `statusPath(Restricted.DENIED)` returns `"dev.kiit"` (no scope set).
 * 2. `statusPath({ ...Restricted.DENIED, scope: "payments.cards" })` returns
 *    `"dev.kiit:payments.cards"`.
 */
export function statusPath(status: Status): string {
  return status.scope.length > 0 ? `${status.origin}:${status.scope}` : status.origin;
}

/**
 * Public, `:`-delimited identity built entirely from hard-rule, compiler-enforced fields
 * (`success`, `group`, `name`). Safe to log, diff, or compare across releases, unlike
 * `statusPath`.
 *
 * Not unique: two statuses can share the same code while differing in `origin`/`scope` (e.g. two
 * different consumers both defining a `Rejected("CONFLICT", ...)`). Don't use this for identity,
 * lookup, or equality checks, compare fields directly instead.
 *
 * Examples:
 * 1. `statusCode(Succeeded.SUCCESS)` returns `"Passed:Succeeded:SUCCESS"`.
 * 2. `statusCode(Restricted.DENIED)` returns `"Failed:Restricted:DENIED"`.
 */
export function statusCode(status: Status): string {
  return `${status.success ? "Passed" : "Failed"}:${status.group}:${status.name}`;
}
