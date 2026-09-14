/**
 * The Code tier of the Status -> Group -> Code taxonomy: the built-in `Codes` set of every code
 * defined in groups.ts, plus protocol mapping (currently HTTP; gRPC and custom composition are
 * deferred).
 *
 * Design notes:
 * 1. `CodesToHttp` is a factory function returning a plain object satisfying `CodeLookup`, not a
 *    class. `CodeLookup` (a plain interface) is what actually enables multiple implementations,
 *    since TypeScript's typing is structural, not nominal, no class/inheritance required. The one
 *    thing a class would uniquely add, subclass-and-override-one-method, is explicitly not the
 *    intended way to customize this: the Kotlin source's own docs say to compose instead (see the
 *    deferred `CompositeLookup`).
 * 2. `CodesToHttp`'s internal `toCode`/`toStatus` are local functions closed over, not object
 *    methods using `this`. A method that reads `this.something` breaks the moment it's destructured
 *    or passed as a callback; a closure over a local variable doesn't have that failure mode.
 */

import { assertNever } from "./assert-never.js";
import {
  Groups,
  Succeeded,
  Pending,
  Excluded,
  Information,
  Restricted,
  Invalid,
  Rejected,
  Unserved,
} from "./groups.js";
import type { Status } from "./status.js";

interface StatusKeyFields {
  readonly origin: string;
  readonly scope: string;
  readonly group: string;
  readonly name: string;
}

/** Module-internal identity: origin+scope+group+name. Not exported, only used for lookups here. */
function statusKey(fields: StatusKeyFields): string {
  return `${fields.origin}:${fields.scope}:${fields.group}:${fields.name}`;
}

const ALL_STATUSES: readonly Status[] = [
  Succeeded.SUCCESS,
  Succeeded.CREATED,
  Succeeded.UPDATED,
  Succeeded.PATCHED,
  Succeeded.FETCHED,
  Succeeded.DELETED,
  Succeeded.HANDLED,
  Succeeded.REFERRED,
  Succeeded.EXITED,
  Pending.ACCEPTED,
  Pending.QUEUED,
  Pending.PROCESSING,
  Pending.CONFIRM,
  Pending.REDIRECTED,
  Pending.SCHEDULED,
  Excluded.OMITTED,
  Excluded.SKIPPED,
  Excluded.DISCARDED,
  Excluded.CANCELLED,
  Excluded.DEDUPLICATED,
  Excluded.DISQUALIFIED,
  Information.NOTICE,
  Information.ADVISORY,
  Information.METADATA,
  Information.HEALTH,
  Information.DIAGNOSTICS,
  Information.MOVED,
  Restricted.DENIED,
  Restricted.UNAUTHENTICATED,
  Restricted.UNAUTHORIZED,
  Restricted.FORBIDDEN,
  Restricted.LOCKED,
  Restricted.SUSPENDED,
  Invalid.INVALID_VALUE,
  Invalid.BAD_REQUEST,
  Invalid.NOT_FOUND,
  Invalid.OUT_OF_RANGE,
  Invalid.PAYLOAD_TOO_LARGE,
  Invalid.MISSING_FIELD,
  Rejected.RULE_VIOLATION,
  Rejected.CONFLICT,
  Rejected.NOT_EXISTS,
  Rejected.PRECONDITION_FAILED,
  Rejected.EXPIRED,
  Rejected.GONE,
  Unserved.UNEXPECTED,
  Unserved.UNSUPPORTED,
  Unserved.TIMEOUT,
  Unserved.RATE_LIMITED,
  Unserved.RESOURCE_LIMITED,
  Unserved.UNREACHABLE,
  Unserved.UNDER_MAINTENANCE,
  Unserved.INTERNAL,
  Unserved.DATA_LOSS,
  Unserved.DEGRADED,
  Unserved.LEGAL_BLOCK,
  Unserved.ABORTED,
];

const byKey = new Map<string, Status>();
for (const status of ALL_STATUSES) {
  const key = statusKey(status);
  if (byKey.has(key)) {
    throw new Error(`Duplicate Status code detected in Codes: ${key}`);
  }
  byKey.set(key, status);
}

/**
 * Built-in set of standard `Status` codes covering common operation outcomes.
 *
 * 1. Using it is optional; these are sensible defaults. Custom codes can be created by calling
 *    any group's own constructor directly (see groups.ts), only the eight groups are fixed.
 * 2. Uniqueness of every built-in status's origin+scope+group+name is enforced at module load
 *    time, immediately above. A collision throws right away instead of surfacing later as a
 *    silent wrong lookup.
 */
export const Codes = {
  /** All built-in codes, in declaration order. Used for reverse lookups, see `CodesToHttp`. */
  all: ALL_STATUSES,

  /** Looks up a built-in status by its origin/group/name, or `undefined` if none matches. */
  statusFor(origin: string, group: string, name: string): Status | undefined {
    return byKey.get(statusKey({ origin, scope: "", group, name }));
  },
};

/**
 * Bidirectional conversion between a `Status` and a target protocol's status code (e.g. HTTP).
 *
 * Implementations should be exhaustive over `Status`'s eight groups, typically via a `switch`
 * with `assertNever` in the `default` branch, so a newly added group is caught at compile time.
 * Individual codes within a group don't need an exhaustive mapping — handle them via a small
 * overrides table layered on top of the group default instead, see `CodesToHttp`.
 */
export interface CodeLookup {
  /** Converts a status to the target protocol's code. */
  toCode(status: Status): number;

  /**
   * Converts a target protocol code to a matching status, or `undefined` if there's no match.
   * The forward direction is typically many-to-one, so this is inherently lossy: it returns *a*
   * status that resolves to `code`, not necessarily the specific one a caller originally had.
   */
  toStatus(code: number): Status | undefined;
}

/**
 * Default `CodeLookup` implementation mapping `Status` to HTTP status codes.
 *
 * Group -> HTTP default: Succeeded/Excluded/Information -> 200, Pending -> 202,
 * Restricted -> 401, Invalid -> 400, Rejected -> 409, Unserved -> 503.
 *
 * Individual codes can differ from their group's default via `overrides` (e.g. CREATED -> 201,
 * NOT_FOUND -> 404). Clients needing additional or custom codes should compose rather than wrap
 * this in inheritance — a future `CompositeLookup` covers that; there's no subclassing story here
 * since this is a factory function, not a class.
 */
export function CodesToHttp(
  overrides: Readonly<Record<string, number>> = CodesToHttp.DEFAULT_OVERRIDES,
): CodeLookup {
  function toCode(status: Status): number {
    const override = overrides[statusKey(status)];
    if (override !== undefined) return override;
    switch (status.group) {
      case Groups.SUCCEEDED:
        return 200;
      case Groups.PENDING:
        return 202;
      case Groups.EXCLUDED:
        return 200;
      case Groups.INFORMATION:
        return 200;
      case Groups.RESTRICTED:
        return 401;
      case Groups.INVALID:
        return 400;
      case Groups.REJECTED:
        return 409;
      case Groups.UNSERVED:
        return 503;
      default:
        return assertNever(status);
    }
  }

  /**
   * Reverse lookup, derived from `toCode` so it can't get out of sync with a custom `overrides`
   * map. Lossy by nature, since many statuses can share one code. Ties break deterministically
   * via `CANONICAL_PREFERENCE` rather than `Codes.all`'s plain declaration order.
   */
  function toStatus(code: number): Status | undefined {
    return (
      CANONICAL_PREFERENCE.find((status) => toCode(status) === code) ??
      Codes.all.find((status) => toCode(status) === code)
    );
  }

  return { toCode, toStatus };
}

export namespace CodesToHttp {
  export const DEFAULT_OVERRIDES: Readonly<Record<string, number>> = {
    [statusKey(Succeeded.CREATED)]: 201,
    [statusKey(Succeeded.HANDLED)]: 204,
    [statusKey(Pending.CONFIRM)]: 200,
    [statusKey(Excluded.CANCELLED)]: 499,
    [statusKey(Pending.REDIRECTED)]: 307,
    [statusKey(Invalid.NOT_FOUND)]: 404,
    [statusKey(Rejected.NOT_EXISTS)]: 404,
    [statusKey(Restricted.FORBIDDEN)]: 403,
    // closer to Forbidden than Unauthenticated, the caller is known
    [statusKey(Restricted.SUSPENDED)]: 403,
    [statusKey(Restricted.LOCKED)]: 423,
    [statusKey(Rejected.EXPIRED)]: 410,
    [statusKey(Rejected.GONE)]: 410,
    // CONFLICT needs no override, 409 is already Rejected's own group default
    [statusKey(Invalid.PAYLOAD_TOO_LARGE)]: 413,
    // HTTP has no separate "unsupported" code
    [statusKey(Unserved.UNSUPPORTED)]: 501,
    // deadline exceeded waiting on something else, not a slow client (408)
    [statusKey(Unserved.TIMEOUT)]: 504,
    [statusKey(Unserved.RATE_LIMITED)]: 429,
    // same axis as RATE_LIMITED, HTTP doesn't distinguish the two
    [statusKey(Unserved.RESOURCE_LIMITED)]: 429,
    [statusKey(Unserved.UNEXPECTED)]: 500,
    [statusKey(Unserved.LEGAL_BLOCK)]: 451,
  };
}

/**
 * One canonical winner per HTTP code that more than one built-in status can resolve to via
 * `toCode`, under `DEFAULT_OVERRIDES` or a group default. See `toStatus`.
 *
 * `422 Unprocessable Entity` has no dedicated status mapping: the code that previously held it,
 * `INVALID_ENTITY`, was removed from `Codes`. `toStatus` returns `undefined` for 422, and
 * anything converting `Invalid.INVALID_VALUE` to HTTP falls through to 400.
 */
const CANONICAL_PREFERENCE: readonly Status[] = [
  Succeeded.SUCCESS,
  Succeeded.CREATED,
  Succeeded.HANDLED,
  Pending.PROCESSING,
  Restricted.UNAUTHENTICATED,
  Restricted.FORBIDDEN,
  Invalid.INVALID_VALUE,
  Invalid.NOT_FOUND,
  Rejected.GONE,
  // RULE_VIOLATION and PRECONDITION_FAILED also fall through to 409, Rejected's own group
  // default. CONFLICT wins since it's the most literal match for the concept.
  Rejected.CONFLICT,
  Unserved.TIMEOUT,
  Unserved.RATE_LIMITED,
  Unserved.UNDER_MAINTENANCE,
];
