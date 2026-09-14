import { describe, expect, it } from "vitest";
import { Err } from "../src/err.js";
import { Restricted, Unserved } from "../src/groups.js";

// Ported from ErrTest.kt.

describe("Err.of", () => {
  it("builds an ErrorInfo", () => {
    const err = Err.of("bad thing");
    expect(err.kind).toBe("ErrorInfo");
    expect(err.message).toBe("bad thing");
    expect(err.cause).toBeUndefined();
  });

  it("carries a cause when given one", () => {
    const root = new Error("root");
    const err = Err.of("bad thing", root);
    expect(err.cause).toBe(root);
  });
});

describe("Err.ofStatus", () => {
  it("uses the status's message", () => {
    const err = Err.ofStatus(Restricted.UNAUTHORIZED);
    expect(err.kind).toBe("ErrorInfo");
    expect(err.message).toBe(Restricted.UNAUTHORIZED.message);
  });
});

describe("Err.on / Err.onField", () => {
  it("builds an ErrorField with field/value/message", () => {
    const err = Err.on("email", "not-an-email", "invalid email");
    expect(err.kind).toBe("ErrorField");
    if (err.kind !== "ErrorField") throw new Error("unreachable");
    expect(err.field).toBe("email");
    expect(err.value).toBe("not-an-email");
    expect(err.message).toBe("invalid email");
  });

  it("onField defaults value to an empty string", () => {
    const err = Err.onField("password", "too short");
    expect(err.kind).toBe("ErrorField");
    if (err.kind !== "ErrorField") throw new Error("unreachable");
    expect(err.field).toBe("password");
    expect(err.value).toBe("");
    expect(err.message).toBe("too short");
  });
});

describe("Err.ex", () => {
  it("builds an ErrorInfo from the Error's own message", () => {
    const root = new Error("boom");
    const err = Err.ex(root);
    expect(err.message).toBe("boom");
    expect(err.cause).toBe(root);
  });
});

describe("Err.obj", () => {
  it("builds an ErrorInfo with the value stringified and kept as ref", () => {
    const payload = { k: "v" };
    const err = Err.obj(payload);
    expect(err.message).toBe(String(payload));
    expect(err.ref).toBe(payload);
  });
});

describe("Err.list", () => {
  it("builds an ErrorList of ErrorInfo entries", () => {
    const err = Err.list(["one", "two"], "multiple errors");
    expect(err.message).toBe("multiple errors");
    expect(err.errors).toHaveLength(2);
    expect(err.errors.every((e) => e.kind === "ErrorInfo")).toBe(true);
  });

  it("defaults the message when none is given", () => {
    const err = Err.list(["one"]);
    expect(err.message).toBe("Error occurred");
  });
});

describe("Err.build", () => {
  it("returns an existing Err unchanged", () => {
    const original = Err.of("already an err");
    expect(Err.build(original)).toBe(original);
  });

  it("wraps a string via of", () => {
    const err = Err.build("plain string");
    expect(err.kind).toBe("ErrorInfo");
    expect(err.message).toBe("plain string");
  });

  it("wraps an Error via ex", () => {
    const root = new Error("failure");
    const err = Err.build(root);
    expect(err.message).toBe("failure");
    expect(err.cause).toBe(root);
  });

  it("wraps other values via obj", () => {
    const err = Err.build(42);
    expect(err.message).toBe("42");
    expect(err.ref).toBe(42);
  });

  it("falls back to Unserved.UNEXPECTED's message for null/undefined", () => {
    expect(Err.build(null).message).toBe(Unserved.UNEXPECTED.message);
    expect(Err.build(undefined).message).toBe(Unserved.UNEXPECTED.message);
  });
});
