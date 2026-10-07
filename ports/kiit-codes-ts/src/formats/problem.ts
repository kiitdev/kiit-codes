/**
 * kiit-side name for RFC 9457's "problem details object" (https://www.rfc-editor.org/rfc/rfc9457.html).
 *
 * {
 *     "type": "https://stripe.com/docs/codes/payments/cards/failed/rejected/duplicate-charge",
 *     "title": "This charge has already been processed",
 *     "status": 409,
 *     "code": "stripe.com:payments.cards:Failed:Rejected:DUPLICATE_CHARGE"
 * }
 *
 * 1. Opt-in output shape, produce one via `ProblemConverter`.
 * 2. For internal service-to-service calls or anywhere else an HTTP status/URI doesn't apply, see
 *    `CodeDetail`, kiit-codes' own native equivalent.
 * 3. `errors` and `code` aren't RFC 9457's own members (`type`/`title`/`status`/`detail`/`instance`) -
 *    they are kiit extensions, allowed under the spec's own provision for extension members. Clients
 *    that don't know them ignore them.
 * 4. `type` is the problem's identity and its docs pointer, compared as an opaque string. `code` is the exact kiit
 *    identity, the same `{origin}:{scope}:{statusCode}` string as `CodeDetail.code`, so a client reads the parts
 *    from it instead of parsing a lowercase `type`. `ProblemConverter` always sets it. It is optional so a
 *    `Problem` built by hand still type-checks.
 * 5. When `code` is missing, the last three segments of a kiit `type` are `status/group/name`, which give
 *    passed or failed and the group, but not the exact name, scope or origin. That is a kiit convention, not
 *    something RFC 9457 expects of consumers.
 */

import type { ErrorItem } from "./error-item.js";

export interface Problem<T extends ErrorItem = ErrorItem> {
  readonly type: string;
  readonly title: string;
  readonly status: number;
  readonly detail?: string;
  readonly instance?: string;
  readonly errors?: readonly T[];
  readonly code?: string;
}
