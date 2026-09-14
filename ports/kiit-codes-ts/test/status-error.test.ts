import { describe, expect, it } from "vitest";
import {
  StatusError,
  RestrictedError,
  InvalidError,
  RejectedError,
  UnservedError,
  toError,
} from "../src/status-error.js";
import { Err } from "../src/err.js";
import { Restricted, Invalid, Rejected, Unserved } from "../src/groups.js";

// Ported from StatusExceptionTest.kt (renamed Exception -> Error, see status-error.ts's top
// comment).

describe("RestrictedError / InvalidError / RejectedError / UnservedError", () => {
  it("each exposes its own status", () => {
    expect(new RestrictedError(Restricted.UNAUTHORIZED).status).toBe(Restricted.UNAUTHORIZED);
    expect(new InvalidError(Invalid.BAD_REQUEST).status).toBe(Invalid.BAD_REQUEST);
    expect(new RejectedError(Rejected.CONFLICT).status).toBe(Rejected.CONFLICT);
    expect(new UnservedError(Unserved.TIMEOUT).status).toBe(Unserved.TIMEOUT);
  });

  it("defaults errors to a single entry wrapping the status", () => {
    const e = new RestrictedError(Restricted.UNAUTHORIZED);
    expect(e.errors).toHaveLength(1);
    expect(e.errors[0]?.message).toBe(Restricted.UNAUTHORIZED.message);
  });

  it("stores explicit errors instead of the default", () => {
    const errors = [Err.of("bad field")];
    const e = new InvalidError(Invalid.BAD_REQUEST, errors);
    expect(e.errors).toEqual(errors);
  });

  it("propagates cause via the standard Error options", () => {
    const root = new Error("root");
    const e = new RejectedError(Rejected.CONFLICT, [], { cause: root });
    expect(e.cause).toBe(root);
  });

  it("takes its message from the underlying status", () => {
    const e = new UnservedError(Unserved.UNREACHABLE);
    expect(e.message).toBe(e.checked.status.message);
  });

  it("sets .name to the concrete subclass name, not the abstract base", () => {
    expect(new RestrictedError(Restricted.UNAUTHORIZED).name).toBe("RestrictedError");
    expect(new InvalidError(Invalid.BAD_REQUEST).name).toBe("InvalidError");
  });

  it("is a real Error and a real StatusError via instanceof", () => {
    const e = new RestrictedError(Restricted.UNAUTHORIZED);
    expect(e).toBeInstanceOf(Error);
    expect(e).toBeInstanceOf(StatusError);
  });

  it("catching as the base StatusError narrows exhaustively over the four subclasses", () => {
    let caught: StatusError | undefined;
    try {
      throw new InvalidError(Invalid.BAD_REQUEST);
    } catch (e) {
      if (e instanceof StatusError) caught = e;
    }
    if (!caught) throw new Error("expected a StatusError to be caught");

    let label: string;
    if (caught instanceof RestrictedError) label = "restricted";
    else if (caught instanceof InvalidError) label = "invalid";
    else if (caught instanceof RejectedError) label = "rejected";
    else if (caught instanceof UnservedError) label = "unserved";
    else throw new Error("unhandled StatusError subclass");

    expect(label).toBe("invalid");
  });

  it("a narrow instanceof check does not match a different subclass", () => {
    let matched = false;
    try {
      throw new InvalidError(Invalid.BAD_REQUEST);
    } catch (e) {
      if (e instanceof RestrictedError) matched = true;
      else if (!(e instanceof InvalidError)) throw e;
    }
    expect(matched).toBe(false);
  });
});

describe("toError", () => {
  it("converts each group to its matching subclass", () => {
    expect(toError(Restricted.UNAUTHORIZED)).toBeInstanceOf(RestrictedError);
    expect(toError(Invalid.BAD_REQUEST)).toBeInstanceOf(InvalidError);
    expect(toError(Rejected.CONFLICT)).toBeInstanceOf(RejectedError);
    expect(toError(Unserved.TIMEOUT)).toBeInstanceOf(UnservedError);
  });

  it("propagates explicit errors", () => {
    const errors = [Err.of("bad field")];
    const e = toError(Invalid.BAD_REQUEST, errors);
    expect(e.errors).toEqual(errors);
  });
});
