/**
 * The Group tier of the Status -> Group -> Code taxonomy: the eight concrete shapes a Status can
 * be, each a plain function merged with a namespace of its built-in Code constants
 * (`Succeeded.SUCCESS`), mirroring Kotlin's constructor-call-plus-companion-object naming without
 * needing `new`. See status.ts for how these compose into `Passed`/`Failed`/`Status` and for the
 * generic operations (`groupDescription`, `statusPath`, `statusCode`) that work over
 * any of them.
 */

/** Well-known Status.origin values. */
export const StatusConstants = {
  /** Origin for every built-in code. Reverse-DNS, mirrors Gradle's groupId, dev.kiit. */
  KIIT: "dev.kiit",
  /** Default origin for consumer/custom statuses that don't specify one explicitly. */
  CUSTOM: "custom",
} as const;

/** The eight group names. */
export const Groups = {
  SUCCEEDED: "Succeeded",
  PENDING: "Pending",
  EXCLUDED: "Excluded",
  INFORMATION: "Information",
  RESTRICTED: "Restricted",
  INVALID: "Invalid",
  REJECTED: "Rejected",
  UNSERVED: "Unserved",
} as const;

/** The group discriminant. See status.ts's top comment for the full hierarchy. */
export type Group = (typeof Groups)[keyof typeof Groups];

/** Fields every Status variant carries, besides its own literal `group`/`success`. */
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
  readonly group: typeof Groups.SUCCEEDED;
  readonly success: true;
}
export function Succeeded(
  name: string,
  message: string,
  origin: string = StatusConstants.CUSTOM,
  scope: string = "",
): Succeeded {
  return { group: Groups.SUCCEEDED, success: true, name, message, origin, scope };
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

/** The operation was accepted but has not yet fully resolved. */
export interface Pending extends StatusFields {
  readonly group: typeof Groups.PENDING;
  readonly success: true;
}
export function Pending(
  name: string,
  message: string,
  origin: string = StatusConstants.CUSTOM,
  scope: string = "",
): Pending {
  return { group: Groups.PENDING, success: true, name, message, origin, scope };
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

/** The item was intentionally excluded from the operation. */
export interface Excluded extends StatusFields {
  readonly group: typeof Groups.EXCLUDED;
  readonly success: true;
}
export function Excluded(
  name: string,
  message: string,
  origin: string = StatusConstants.CUSTOM,
  scope: string = "",
): Excluded {
  return { group: Groups.EXCLUDED, success: true, name, message, origin, scope };
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

/** The response provides information; no operation was performed. */
export interface Information extends StatusFields {
  readonly group: typeof Groups.INFORMATION;
  readonly success: true;
}
export function Information(
  name: string,
  message: string,
  origin: string = StatusConstants.CUSTOM,
  scope: string = "",
): Information {
  return { group: Groups.INFORMATION, success: true, name, message, origin, scope };
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

/** The caller is not allowed. */
export interface Restricted extends StatusFields {
  readonly group: typeof Groups.RESTRICTED;
  readonly success: false;
}
export function Restricted(
  name: string,
  message: string,
  origin: string = StatusConstants.CUSTOM,
  scope: string = "",
): Restricted {
  return { group: Groups.RESTRICTED, success: false, name, message, origin, scope };
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

/** The request itself is wrong. */
export interface Invalid extends StatusFields {
  readonly group: typeof Groups.INVALID;
  readonly success: false;
}
export function Invalid(
  name: string,
  message: string,
  origin: string = StatusConstants.CUSTOM,
  scope: string = "",
): Invalid {
  return { group: Groups.INVALID, success: false, name, message, origin, scope };
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

/** The caller was allowed, but the business refuses it. */
export interface Rejected extends StatusFields {
  readonly group: typeof Groups.REJECTED;
  readonly success: false;
}
export function Rejected(
  name: string,
  message: string,
  origin: string = StatusConstants.CUSTOM,
  scope: string = "",
): Rejected {
  return { group: Groups.REJECTED, success: false, name, message, origin, scope };
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

/**
 * The system can't serve it right now, though nothing was wrong with the request. Covers
 * capacity, timeout, an unsupported capability, planned maintenance, a degraded or aborted
 * dependency, a legal/regulatory block, or a genuinely unexpected/unhandled failure.
 */
export interface Unserved extends StatusFields {
  readonly group: typeof Groups.UNSERVED;
  readonly success: false;
}
export function Unserved(
  name: string,
  message: string,
  origin: string = StatusConstants.CUSTOM,
  scope: string = "",
): Unserved {
  return { group: Groups.UNSERVED, success: false, name, message, origin, scope };
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
