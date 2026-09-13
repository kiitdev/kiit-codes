/**
 * Exhaustiveness check for a `switch` over a discriminated union. TypeScript narrows the switched
 * value to `never` once every case is handled, so a call here only type-checks when every branch
 * is covered. Adding a new group without updating every switch is now a compile error, not a
 * silent runtime gap.
 */
export function assertNever(value: never): never {
  throw new Error(`Unreachable case: ${JSON.stringify(value)}`);
}
