/**
 * kiit-side name for RFC 9457's "problem details object" (https://www.rfc-editor.org/rfc/rfc9457.html).
 *
 * {
 *     "type": "https://stripe.com/problems/payments.cards/rejected/duplicate-charge",
 *     "title": "This charge has already been processed",
 *     "status": 409
 * }
 *
 * 1. Opt-in output shape, build one via `CodesToProblem`.
 * 2. For internal service-to-service calls or anywhere else an HTTP status/URI doesn't apply, see
 *    `CodeDetail`, kiit-codes' own native equivalent.
 * 3. `errors` isn't one of RFC 9457's own members (`type`/`title`/`status`/`detail`/`instance`) -
 *    it's a kiit extension, allowed under the spec's own provision for extension members.
 */

import type { ErrorItem } from "./error-item.js";

export interface Problem<T extends ErrorItem = ErrorItem> {
  readonly type: string;
  readonly title: string;
  readonly status: number;
  readonly detail?: string;
  readonly instance?: string;
  readonly errors?: readonly T[];
}
