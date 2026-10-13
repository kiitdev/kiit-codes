/**
 * The rules for a Status's `origin`, `scope` and `name`. They are joined with `:` into `statusCode` and with `/` into
 * the RFC 9457 `type`, so a stray delimiter would silently change both. Every group constructor checks them and throws
 * an `Error` for a bad value. A plain object, such as one from `JSON.parse` or a spread, is not checked.
 *
 * 1. Allowed characters are ASCII letters, digits, `.`, `-` and `_`. Case is not enforced and there is no length limit.
 * 2. A value that is not empty starts with a letter or digit, so `.` and `..` can't become a path segment.
 * 3. `origin` and `name` must not be empty. `scope` may be empty, which means unset.
 * 4. `kiit.dev` is reserved for kiit-authored codes. This is a convention and is not checked.
 *
 * Values are constants in source, so a bad one is a programmer error and throws. For a value that comes from config or
 * other runtime input, call `check` first, it never throws:
 *
 * ```ts
 * const error = StatusRules.check(cfg.origin, cfg.scope, "PAYMENT_DECLINED");
 * if (error === null) Rejected("PAYMENT_DECLINED", "Declined", cfg.origin, cfg.scope);
 * ```
 */
export const StatusRules = {
  /**
   * Checks the three values and returns the first problem as a message, or null when all are valid.
   * The message names the field and shows the value with non-printable characters escaped and long values cut.
   */
  check(origin: string, scope: string, name: string): string | null {
    return checkField("origin", origin, false) ?? checkField("scope", scope, true) ?? checkField("name", name, false);
  },
} as const;

/** Throws an `Error` with the message from `StatusRules.check`, if there is one. Not exported from the package. */
export function validateStatusFields(origin: string, scope: string, name: string): void {
  const error = StatusRules.check(origin, scope, name);
  if (error !== null) throw new Error(error);
}

/**
 * How many characters of a bad value an error message shows, the rest is replaced by `...`. It is not a limit on the
 * field, which has none. It only keeps a long value from runtime input out of exception messages and logs. The cut can
 * hide the bad character, the reason text still names the allowed ones.
 */
const MAX_SHOWN = 32;

function checkField(field: string, value: string, allowEmpty: boolean): string | null {
  if (value.length === 0) return allowEmpty ? null : `${field} must not be empty`;
  if (!isAlphaNumeric(value.charCodeAt(0))) return invalid(field, value, "must start with a letter or digit");
  for (let i = 0; i < value.length; i++) {
    if (!isAllowed(value.charCodeAt(i))) {
      return invalid(field, value, "allowed characters are a-z, A-Z, 0-9, '.', '-', '_'");
    }
  }
  return null;
}

function isAlphaNumeric(c: number): boolean {
  return (c >= 97 && c <= 122) || (c >= 65 && c <= 90) || (c >= 48 && c <= 57);
}

function isAllowed(c: number): boolean {
  return isAlphaNumeric(c) || c === 46 || c === 45 || c === 95;
}

function invalid(field: string, value: string, reason: string): string {
  return `${field} "${shown(value)}" is invalid: ${reason}`;
}

function shown(value: string): string {
  const head = value.length > MAX_SHOWN ? value.substring(0, MAX_SHOWN) : value;
  let escaped = "";
  for (let i = 0; i < head.length; i++) {
    const c = head.charCodeAt(i);
    escaped += c >= 0x20 && c <= 0x7e ? head[i] : `\\u${c.toString(16).padStart(4, "0")}`;
  }
  return value.length > MAX_SHOWN ? `${escaped}...` : escaped;
}
