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
 * function directly (see codes.ts for the built-in set).
 *
 *   Status  = Passed     | Failed
 *   Passed  = Succeeded  | Pending | Excluded | Information
 *   Failed  = Restricted | Invalid | Rejected | Unserved
 *
 * `Status` is a real union of eight separate variant interfaces below, not one interface with a
 * union-typed `group` field. That distinction is what lets a `switch (status.group)` narrow the
 * whole object, not just the `group` property, which is what makes `assertNever` in the `default`
 * branch a genuine compile-time exhaustiveness check.
 */

import { assertNever } from "./assert-never.js";

/** Well-known Status.origin values. */
export const StatusConstants = {
  /** Origin for every built-in code. Reverse-DNS, mirrors Gradle's groupId, dev.kiit. */
  KIIT: "dev.kiit",
  /** Default origin for consumer/custom statuses that don't specify one explicitly. */
  CUSTOM: "custom",
} as const;

/** The group discriminant. See the hierarchy in this file's top comment. */
export type Group =
  | "Succeeded"
  | "Pending"
  | "Excluded"
  | "Information"
  | "Restricted"
  | "Invalid"
  | "Rejected"
  | "Unserved";

/**
 * Fields every Status variant carries. Each interface below repeats `group`/`success` with its
 * own literal value rather than inheriting them, so each variant narrows independently.
 */
interface StatusFields {
  /**
   * Unique domain label, e.g. "TOKEN_EXPIRED", "RATE_LIMITED". SCREAMING_SNAKE_CASE and stable,
   * used as a searchable/aggregable key in logs and metrics.
   */
  readonly name: string;

  /**
   * Origin of this status, e.g. StatusConstants.KIIT for every built-in code. Custom statuses
   * default to StatusConstants.CUSTOM rather than silently inheriting KIIT, so a status can never
   * accidentally misrepresent where it came from.
   */
  readonly origin: string;

  /**
   * Optional, free-form internal-organization label: a department, product area, route, or
   * anything else the consumer wants to attach, e.g. "payments", "payments.cards". kiit-codes
   * never parses or enforces scope's internal shape, only that it doesn't contain `:`.
   *
   * Empty string means unset. Defaults to "" on every built-in and on any custom status that
   * doesn't set it explicitly.
   */
  readonly scope: string;

  /**
   * Human-readable constant description, never built from runtime data. Per-instance detail
   * belongs on whatever wraps this Status, not here. Don't use this as a key, use `name` instead.
   */
  readonly message: string;
}

/** The operation completed successfully. */
export interface Succeeded extends StatusFields {
  readonly group: "Succeeded";
  readonly success: true;
}

/** The operation was accepted but has not yet fully resolved. */
export interface Pending extends StatusFields {
  readonly group: "Pending";
  readonly success: true;
}

/** The item was intentionally excluded from the operation. */
export interface Excluded extends StatusFields {
  readonly group: "Excluded";
  readonly success: true;
}

/** The response provides information; no operation was performed. */
export interface Information extends StatusFields {
  readonly group: "Information";
  readonly success: true;
}

/** The caller is not allowed. */
export interface Restricted extends StatusFields {
  readonly group: "Restricted";
  readonly success: false;
}

/** The request itself is wrong. */
export interface Invalid extends StatusFields {
  readonly group: "Invalid";
  readonly success: false;
}

/** The caller was allowed, but the business refuses it. */
export interface Rejected extends StatusFields {
  readonly group: "Rejected";
  readonly success: false;
}

/**
 * The system can't serve it right now, though nothing was wrong with the request. Covers
 * capacity, timeout, an unsupported capability, planned maintenance, a degraded or aborted
 * dependency, a legal/regulatory block, or a genuinely unexpected/unhandled failure.
 */
export interface Unserved extends StatusFields {
  readonly group: "Unserved";
  readonly success: false;
}

/** Every non-failure status. */
export type Passed = Succeeded | Pending | Excluded | Information;

/** Every failure status. */
export type Failed = Restricted | Invalid | Rejected | Unserved;

/** Any status, success or failure. */
export type Status = Passed | Failed;

/** Constructs a custom `Succeeded` status. See `Succeeded`'s built-ins for the KIIT-origin set. */
export function Succeeded(
  name: string,
  message: string,
  origin: string = StatusConstants.CUSTOM,
  scope: string = "",
): Succeeded {
  return { group: "Succeeded", success: true, name, message, origin, scope };
}

export namespace Succeeded {
  export const SUCCESS: Succeeded = Succeeded(
    "SUCCESS",
    "The operation completed successfully.",
    StatusConstants.KIIT,
  );
  export const CREATED: Succeeded = Succeeded(
    "CREATED",
    "A new resource was created.",
    StatusConstants.KIIT,
  );
  export const UPDATED: Succeeded = Succeeded(
    "UPDATED",
    "The resource was fully updated.",
    StatusConstants.KIIT,
  );
  export const PATCHED: Succeeded = Succeeded(
    "PATCHED",
    "The resource was partially updated.",
    StatusConstants.KIIT,
  );
  export const FETCHED: Succeeded = Succeeded(
    "FETCHED",
    "The resource was retrieved.",
    StatusConstants.KIIT,
  );
  export const DELETED: Succeeded = Succeeded(
    "DELETED",
    "The resource was deleted.",
    StatusConstants.KIIT,
  );
  export const HANDLED: Succeeded = Succeeded(
    "HANDLED",
    "The request was handled; nothing to return.",
    StatusConstants.KIIT,
  );
  export const REFERRED: Succeeded = Succeeded(
    "REFERRED",
    "The result is at another location.",
    StatusConstants.KIIT,
  );
  export const EXITED: Succeeded = Succeeded(
    "EXITED",
    "The application exited cleanly.",
    StatusConstants.KIIT,
  );
}

/** Constructs a custom `Pending` status. See `Pending`'s built-ins for the KIIT-origin set. */
export function Pending(
  name: string,
  message: string,
  origin: string = StatusConstants.CUSTOM,
  scope: string = "",
): Pending {
  return { group: "Pending", success: true, name, message, origin, scope };
}

export namespace Pending {
  export const ACCEPTED: Pending = Pending(
    "ACCEPTED",
    "The request was accepted.",
    StatusConstants.KIIT,
  );
  export const QUEUED: Pending = Pending(
    "QUEUED",
    "The request is waiting to be processed.",
    StatusConstants.KIIT,
  );
  export const PROCESSING: Pending = Pending(
    "PROCESSING",
    "The request is being processed.",
    StatusConstants.KIIT,
  );
  export const CONFIRM: Pending = Pending(
    "CONFIRM",
    "The request is awaiting confirmation.",
    StatusConstants.KIIT,
  );
  export const REDIRECTED: Pending = Pending(
    "REDIRECTED",
    "This request is being handled elsewhere.",
    StatusConstants.KIIT,
  );
  export const SCHEDULED: Pending = Pending(
    "SCHEDULED",
    "The operation is scheduled for later.",
    StatusConstants.KIIT,
  );
}

/** Constructs a custom `Excluded` status. See `Excluded`'s built-ins for the KIIT-origin set. */
export function Excluded(
  name: string,
  message: string,
  origin: string = StatusConstants.CUSTOM,
  scope: string = "",
): Excluded {
  return { group: "Excluded", success: true, name, message, origin, scope };
}

export namespace Excluded {
  export const OMITTED: Excluded = Excluded(
    "OMITTED",
    "The item was excluded from the result.",
    StatusConstants.KIIT,
  );
  export const SKIPPED: Excluded = Excluded(
    "SKIPPED",
    "The item was not processed.",
    StatusConstants.KIIT,
  );
  export const DISCARDED: Excluded = Excluded(
    "DISCARDED",
    "The item was processed, then excluded for unrelated reasons.",
    StatusConstants.KIIT,
  );
  export const CANCELLED: Excluded = Excluded(
    "CANCELLED",
    "The operation was cancelled by the caller before completion.",
    StatusConstants.KIIT,
  );
  export const DEDUPLICATED: Excluded = Excluded(
    "DEDUPLICATED",
    "The duplicate item was not processed.",
    StatusConstants.KIIT,
  );
  export const DISQUALIFIED: Excluded = Excluded(
    "DISQUALIFIED",
    "The item was disqualified.",
    StatusConstants.KIIT,
  );
}

/** Constructs a custom `Information` status. See `Information`'s built-ins for the KIIT-origin set. */
export function Information(
  name: string,
  message: string,
  origin: string = StatusConstants.CUSTOM,
  scope: string = "",
): Information {
  return { group: "Information", success: true, name, message, origin, scope };
}

export namespace Information {
  export const NOTICE: Information = Information(
    "NOTICE",
    "An informational notice.",
    StatusConstants.KIIT,
  );
  export const ADVISORY: Information = Information(
    "ADVISORY",
    "A notice that may need attention.",
    StatusConstants.KIIT,
  );
  export const METADATA: Information = Information(
    "METADATA",
    "Information about the application itself was returned.",
    StatusConstants.KIIT,
  );
  export const HEALTH: Information = Information(
    "HEALTH",
    "The service is healthy and operational.",
    StatusConstants.KIIT,
  );
  export const DIAGNOSTICS: Information = Information(
    "DIAGNOSTICS",
    "Diagnostic or operational information was returned.",
    StatusConstants.KIIT,
  );
  export const MOVED: Information = Information(
    "MOVED",
    "The resource has permanently moved to a new location.",
    StatusConstants.KIIT,
  );
}

/** Constructs a custom `Restricted` status. See `Restricted`'s built-ins for the KIIT-origin set. */
export function Restricted(
  name: string,
  message: string,
  origin: string = StatusConstants.CUSTOM,
  scope: string = "",
): Restricted {
  return { group: "Restricted", success: false, name, message, origin, scope };
}

export namespace Restricted {
  export const DENIED: Restricted = Restricted(
    "DENIED",
    "The request was denied.",
    StatusConstants.KIIT,
  );
  export const UNAUTHENTICATED: Restricted = Restricted(
    "UNAUTHENTICATED",
    "Authentication is required.",
    StatusConstants.KIIT,
  );
  export const UNAUTHORIZED: Restricted = Restricted(
    "UNAUTHORIZED",
    "The caller lacks permission.",
    StatusConstants.KIIT,
  );
  export const FORBIDDEN: Restricted = Restricted(
    "FORBIDDEN",
    "Access to this resource is forbidden.",
    StatusConstants.KIIT,
  );
  export const LOCKED: Restricted = Restricted(
    "LOCKED",
    "Access is locked; resolve the condition to restore access.",
    StatusConstants.KIIT,
  );
  export const SUSPENDED: Restricted = Restricted(
    "SUSPENDED",
    "Access has been administratively suspended.",
    StatusConstants.KIIT,
  );
}

/** Constructs a custom `Invalid` status. See `Invalid`'s built-ins for the KIIT-origin set. */
export function Invalid(
  name: string,
  message: string,
  origin: string = StatusConstants.CUSTOM,
  scope: string = "",
): Invalid {
  return { group: "Invalid", success: false, name, message, origin, scope };
}

export namespace Invalid {
  export const INVALID_VALUE: Invalid = Invalid(
    "INVALID_VALUE",
    "The request had an invalid value.",
    StatusConstants.KIIT,
  );
  export const BAD_REQUEST: Invalid = Invalid(
    "BAD_REQUEST",
    "The request was malformed.",
    StatusConstants.KIIT,
  );
  export const NOT_FOUND: Invalid = Invalid(
    "NOT_FOUND",
    "The requested route or endpoint does not exist.",
    StatusConstants.KIIT,
  );
  export const OUT_OF_RANGE: Invalid = Invalid(
    "OUT_OF_RANGE",
    "A value was outside the acceptable range.",
    StatusConstants.KIIT,
  );
  export const PAYLOAD_TOO_LARGE: Invalid = Invalid(
    "PAYLOAD_TOO_LARGE",
    "The payload is too large.",
    StatusConstants.KIIT,
  );
  export const MISSING_FIELD: Invalid = Invalid(
    "MISSING_FIELD",
    "A required field was not provided.",
    StatusConstants.KIIT,
  );
}

/** Constructs a custom `Rejected` status. See `Rejected`'s built-ins for the KIIT-origin set. */
export function Rejected(
  name: string,
  message: string,
  origin: string = StatusConstants.CUSTOM,
  scope: string = "",
): Rejected {
  return { group: "Rejected", success: false, name, message, origin, scope };
}

export namespace Rejected {
  export const RULE_VIOLATION: Rejected = Rejected(
    "RULE_VIOLATION",
    "A business rule rejected the request.",
    StatusConstants.KIIT,
  );
  export const CONFLICT: Rejected = Rejected(
    "CONFLICT",
    "The request conflicts with the current state.",
    StatusConstants.KIIT,
  );
  export const NOT_EXISTS: Rejected = Rejected(
    "NOT_EXISTS",
    "The referenced item does not exist.",
    StatusConstants.KIIT,
  );
  export const PRECONDITION_FAILED: Rejected = Rejected(
    "PRECONDITION_FAILED",
    "A required precondition was not met.",
    StatusConstants.KIIT,
  );
  export const EXPIRED: Rejected = Rejected(
    "EXPIRED",
    "The item has expired.",
    StatusConstants.KIIT,
  );
  export const GONE: Rejected = Rejected(
    "GONE",
    "The resource was removed and is no longer available.",
    StatusConstants.KIIT,
  );
}

/** Constructs a custom `Unserved` status. See `Unserved`'s built-ins for the KIIT-origin set. */
export function Unserved(
  name: string,
  message: string,
  origin: string = StatusConstants.CUSTOM,
  scope: string = "",
): Unserved {
  return { group: "Unserved", success: false, name, message, origin, scope };
}

export namespace Unserved {
  export const UNEXPECTED: Unserved = Unserved(
    "UNEXPECTED",
    "An unexpected, unclassified error occurred.",
    StatusConstants.KIIT,
  );
  export const UNSUPPORTED: Unserved = Unserved(
    "UNSUPPORTED",
    "This capability is not currently available.",
    StatusConstants.KIIT,
  );
  export const TIMEOUT: Unserved = Unserved(
    "TIMEOUT",
    "The operation timed out.",
    StatusConstants.KIIT,
  );
  export const RATE_LIMITED: Unserved = Unserved(
    "RATE_LIMITED",
    "Too many requests; try again later.",
    StatusConstants.KIIT,
  );
  export const RESOURCE_LIMITED: Unserved = Unserved(
    "RESOURCE_LIMITED",
    "A resource limit has been reached.",
    StatusConstants.KIIT,
  );
  export const UNREACHABLE: Unserved = Unserved(
    "UNREACHABLE",
    "A required dependency could not be reached.",
    StatusConstants.KIIT,
  );
  export const UNDER_MAINTENANCE: Unserved = Unserved(
    "UNDER_MAINTENANCE",
    "The service is temporarily under maintenance.",
    StatusConstants.KIIT,
  );
  export const INTERNAL: Unserved = Unserved(
    "INTERNAL",
    "An internal invariant was violated.",
    StatusConstants.KIIT,
  );
  export const DATA_LOSS: Unserved = Unserved(
    "DATA_LOSS",
    "Unrecoverable data loss or corruption occurred.",
    StatusConstants.KIIT,
  );
  export const DEGRADED: Unserved = Unserved(
    "DEGRADED",
    "This dependency is degraded; some calls may be refused.",
    StatusConstants.KIIT,
  );
  export const LEGAL_BLOCK: Unserved = Unserved(
    "LEGAL_BLOCK",
    "Access is blocked for legal reasons.",
    StatusConstants.KIIT,
  );
  export const ABORTED: Unserved = Unserved(
    "ABORTED",
    "The operation was aborted; retrying may help.",
    StatusConstants.KIIT,
  );
}

/** This group's plain-language description. Exhaustive over every group; see this file's top comment for the full hierarchy. */
export function groupDescription(status: Status): string {
  switch (status.group) {
    case "Succeeded":
      return "The operation completed successfully.";
    case "Pending":
      return "The operation was accepted but has not yet fully resolved.";
    case "Excluded":
      return "The item was intentionally excluded from the operation.";
    case "Information":
      return "The response provides information; no operation was performed.";
    case "Restricted":
      return "The caller is not allowed.";
    case "Invalid":
      return "The request itself is wrong.";
    case "Rejected":
      return "The caller was allowed, but the business refuses it.";
    case "Unserved":
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

/**
 * Resolves a status from an optional `message` override and an optional `rawStatus` override,
 * falling back to `status` when neither is supplied. `rawStatus`, if present, is used as the base
 * instead of `status`; `message`, if present, is then applied on top of that base.
 */
export function ofStatus<T extends Status>(
  message: string | undefined,
  rawStatus: T | undefined,
  status: T,
): T {
  const base = rawStatus ?? status;
  return message === undefined ? base : { ...base, message };
}
