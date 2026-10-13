import { describe, expect, it } from "vitest";
import { Succeeded, Pending, Excluded, Information, Restricted, Invalid, Rejected, Unserved } from "../src/groups.js";
import { StatusRules } from "../src/index.js";
import { Codes } from "../src/codes.js";

// Ported from StatusRulesTest.kt. Keep the vectors in both files the same.

const validOrigins = ["stripe.com", "Stripe.com", "myapp1", "api.my-shop.example.com", "my_app", "9lives", "o", "a".repeat(500)];
const invalidOrigins = [
  "", "host:8080", "a/b", "a b", "a?b", "a#b", "a%b", "a\\b", 'a"b', ".", "..", "-x", "_x", ".x",
  "é", "a\tb", "a\nb", "a b", "a b", " stripe.com",
];
const validScopes = ["", "payments", "payments.cards", "Pay_ments.Cards", "a-b_c", "a..b", "9", "a".repeat(500)];
const invalidScopes = [" ", "a:b", "a/b", "a b", ".", "..", "-x", "_x", "é", "a\nb"];
const validNames = ["PAYMENT_DECLINED", "out_of_stock", "A", "X9", "a-b", "a.b", "1ST"];
const invalidNames = ["", "A:B", "A/B", "A B", "A?B", "A#B", "A%B", ".", "..", "-A", "_A", "É", "A\nB"];

describe("StatusRules.check", () => {
  it("valid values pass", () => {
    for (const v of validOrigins) expect(StatusRules.check(v, "", "N"), `origin '${v}'`).toBeNull();
    for (const v of validScopes) expect(StatusRules.check("o", v, "N"), `scope '${v}'`).toBeNull();
    for (const v of validNames) expect(StatusRules.check("o", "", v), `name '${v}'`).toBeNull();
  });

  it("invalid values fail", () => {
    for (const v of invalidOrigins) expect(StatusRules.check(v, "", "N"), `origin '${v}'`).not.toBeNull();
    for (const v of invalidScopes) expect(StatusRules.check("o", v, "N"), `scope '${v}'`).not.toBeNull();
    for (const v of invalidNames) expect(StatusRules.check("o", "", v), `name '${v}'`).not.toBeNull();
  });

  it("empty scope is unset but empty origin and name are rejected", () => {
    expect(StatusRules.check("o", "", "N")).toBeNull();
    expect(StatusRules.check("", "", "N")).toBe("origin must not be empty");
    expect(StatusRules.check("o", "", "")).toBe("name must not be empty");
  });

  it("the message names the field and the value", () => {
    expect(StatusRules.check("host:8080", "", "N")).toBe(
      `origin "host:8080" is invalid: allowed characters are a-z, A-Z, 0-9, '.', '-', '_'`,
    );
    expect(StatusRules.check("o", "", "..")).toBe(`name ".." is invalid: must start with a letter or digit`);
  });

  it("the message escapes control characters and cuts long values", () => {
    const message = StatusRules.check("o", "", "A\nB");
    expect(message).toContain("A\\u000aB");
    expect(message).not.toContain("\n");

    const long = StatusRules.check("o", "", "A".repeat(100) + ":");
    expect(long).toContain(`"${"A".repeat(32)}..."`);
  });

  it("reports origin before scope before name", () => {
    expect(StatusRules.check("a:b", "c:d", "E:F")).toMatch(/^origin/);
    expect(StatusRules.check("o", "c:d", "E:F")).toMatch(/^scope/);
  });
});

describe("group constructors", () => {
  const builders: Array<[string, (o: string, s: string, n: string) => unknown]> = [
    ["Succeeded", (o, s, n) => Succeeded(n, "t", o, s)],
    ["Pending", (o, s, n) => Pending(n, "t", o, s)],
    ["Excluded", (o, s, n) => Excluded(n, "t", o, s)],
    ["Information", (o, s, n) => Information(n, "t", o, s)],
    ["Restricted", (o, s, n) => Restricted(n, "t", o, s)],
    ["Invalid", (o, s, n) => Invalid(n, "t", o, s)],
    ["Rejected", (o, s, n) => Rejected(n, "t", o, s)],
    ["Unserved", (o, s, n) => Unserved(n, "t", o, s)],
  ];

  it.each(builders)("%s throws on bad values", (_group, build) => {
    expect(() => build("stripe.com", "payments.cards", "N")).not.toThrow();
    expect(() => build("host:8080", "", "N")).toThrow(Error);
    expect(() => build("o", "a/b", "N")).toThrow(Error);
    expect(() => build("o", "", "A B")).toThrow(Error);
    expect(() => build("", "", "N")).toThrow(Error);
  });

  it("the thrown message is the check message", () => {
    expect(() => Rejected("A:B", "t")).toThrow(StatusRules.check("custom", "", "A:B")!);
  });

  it("every built-in passes", () => {
    for (const s of Codes.all) expect(StatusRules.check(s.origin, s.scope, s.name), s.name).toBeNull();
  });

  it("a plain object is not checked", () => {
    expect(() => ({ ...Rejected.CONFLICT, scope: "a/b" })).not.toThrow();
  });
});
