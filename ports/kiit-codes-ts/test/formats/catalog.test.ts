import { describe, expect, it } from "vitest";
import { Catalog, baseUrlFor } from "../../src/formats/catalog.js";
import { StatusConstants } from "../../src/groups.js";

// Ported from formats/CatalogTest.kt.

describe("Catalog", () => {
  it("KIIT origin defaults when not supplied", () => {
    expect(baseUrlFor(Catalog.of(), StatusConstants.KIIT)).toBe("https://www.kiit.dev/docs/kiit-codes");
  });

  it("an unregistered origin returns undefined", () => {
    expect(baseUrlFor(Catalog.of(), "com.stripe")).toBeUndefined();
  });

  it("of adds each supplied origin", () => {
    const catalog = Catalog.of({ "com.stripe": "https://stripe.com/problems" });
    expect(baseUrlFor(catalog, "com.stripe")).toBe("https://stripe.com/problems");
  });

  it("of ignores a supplied KIIT override", () => {
    const catalog = Catalog.of({ [StatusConstants.KIIT]: "https://example.com/problems" });
    expect(baseUrlFor(catalog, StatusConstants.KIIT)).toBe("https://www.kiit.dev/docs/kiit-codes");
  });
});
