import { describe, expect, it } from "vitest";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import { Codes } from "../src/codes.js";
import { groupDescription } from "../src/status.js";
import {
  Succeeded,
  Pending,
  Excluded,
  Information,
  Restricted,
  Invalid,
  Rejected,
  Unserved,
} from "../src/groups.js";

/**
 * Cross-checks this TS port's `Codes` directly against the Kotlin source text, so a future
 * Kotlin change (a renamed/added/removed code, a reworded message) fails this test instead of
 * silently drifting. Depends on this repo's layout: kiit-codes-kotlin/ as a sibling of ports/.
 */

const KOTLIN_STATUS_PATH = fileURLToPath(
  new URL("../../../kiit-codes-kotlin/kiit-codes/src/commonMain/kotlin/kiit/codes/Status.kt", import.meta.url),
);

const GROUP_NAMES = [
  "Succeeded",
  "Pending",
  "Excluded",
  "Information",
  "Restricted",
  "Invalid",
  "Rejected",
  "Unserved",
] as const;

function extractKotlinCodes(text: string): Array<{ group: string; name: string; message: string }> {
  const re =
    /val\s+\w+\s*=\s*\n\s*(Succeeded|Pending|Excluded|Information|Restricted|Invalid|Rejected|Unserved)\(\s*\n\s*"([^"]+)",\s*\n\s*"([^"]+)",\s*\n\s*origin\s*=\s*StatusConstants\.KIIT,/g;
  const out: Array<{ group: string; name: string; message: string }> = [];
  for (const m of text.matchAll(re)) {
    out.push({ group: m[1] as string, name: m[2] as string, message: m[3] as string });
  }
  return out;
}

function extractKotlinGroupDescriptions(text: string): Map<string, string> {
  const re =
    /is\s+(Succeeded|Pending|Excluded|Information|Restricted|Invalid|Rejected|Unserved)\s*->\s*"([^"]+)"/g;
  const out = new Map<string, string>();
  for (const m of text.matchAll(re)) {
    out.set(m[1] as string, m[2] as string);
  }
  return out;
}

const kotlinSource = readFileSync(KOTLIN_STATUS_PATH, "utf8");
const kotlinCodes = extractKotlinCodes(kotlinSource);
const kotlinGroupDescriptions = extractKotlinGroupDescriptions(kotlinSource);

const GROUP_FACTORIES = {
  Succeeded,
  Pending,
  Excluded,
  Information,
  Restricted,
  Invalid,
  Rejected,
  Unserved,
} as const;

describe("Codes parity vs. Kotlin's Status.kt", () => {
  it("extracted at least one code per group from the Kotlin source (sanity check on the regex itself)", () => {
    for (const group of GROUP_NAMES) {
      expect(kotlinCodes.some((c) => c.group === group), `no codes extracted for ${group}`).toBe(true);
    }
  });

  it("has exactly the same set of built-in codes as Kotlin, with matching messages", () => {
    expect(Codes.all.length).toBe(kotlinCodes.length);

    for (const { group, name, message } of kotlinCodes) {
      const factory = GROUP_FACTORIES[group as keyof typeof GROUP_FACTORIES];
      const tsStatus = (factory as unknown as Record<string, { message: string } | undefined>)[name];
      expect(tsStatus, `missing ${group}.${name} in the TS port`).toBeDefined();
      expect(tsStatus?.message, `${group}.${name} message mismatch`).toBe(message);
    }
  });

  it("Codes.all doesn't contain anything Kotlin doesn't have", () => {
    const kotlinKeys = new Set(kotlinCodes.map((c) => `${c.group}:${c.name}`));
    for (const status of Codes.all) {
      const key = `${status.group}:${status.name}`;
      expect(kotlinKeys.has(key), `${key} exists in the TS port but not in Kotlin's Status.kt`).toBe(true);
    }
  });

  it("groupDescription matches Kotlin's for every group", () => {
    expect(kotlinGroupDescriptions.size).toBe(8);
    for (const [group, description] of kotlinGroupDescriptions) {
      const sample = GROUP_FACTORIES[group as keyof typeof GROUP_FACTORIES]("X", "X");
      expect(groupDescription(sample), `groupDescription mismatch for ${group}`).toBe(description);
    }
  });
});
