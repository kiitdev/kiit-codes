/**
 * Immutable map of a Status's `origin` to the `baseUrl` `CodesToProblem` should use for it. Build
 * one via `Catalog.of`.
 *
 * `StatusConstants.KIIT` is always present and always resolves to kiit-codes' own taxonomy docs -
 * `of`'s `baseUrls` cannot override it, kiit-origin statuses always point back here.
 *
 * A plain object, not a class: there's no behavior here beyond a lookup, so a class would add
 * ceremony (a constructor, `new`) for no benefit over a map with one fixed entry merged in.
 */

import { StatusConstants } from "../groups.js";

const DEFAULT_KIIT_BASE_URL = "https://www.kiit.dev/docs/kiit-codes";

export type Catalog = Readonly<Record<string, string>>;

export const Catalog = {
  /** Builds a Catalog from `baseUrls`, with `StatusConstants.KIIT` fixed to its own docs. */
  of(baseUrls: Readonly<Record<string, string>> = {}): Catalog {
    return { ...baseUrls, [StatusConstants.KIIT]: DEFAULT_KIIT_BASE_URL };
  },
};

/** The registered `baseUrl` for `origin`, or `undefined` if none was passed to `Catalog.of`. */
export function baseUrlFor(catalog: Catalog, origin: string): string | undefined {
  return catalog[origin];
}
