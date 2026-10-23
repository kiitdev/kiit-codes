#!/usr/bin/env bash
set -euo pipefail

# Packs the built package and loads the tarball from an ESM project and a CommonJS project, the way
# a consumer does. Checks that import and require() both work, that they return the same module
# object (no second copy of the Codes registry or the typed errors), and that the types resolve
# under nodenext and bundler. Run after `npm run build`:
#
#   npm run test:consumers

PKG_DIR="$(cd "$(dirname "$0")/.." && pwd)"
TSC="$PKG_DIR/node_modules/.bin/tsc"

WORKDIR="$(mktemp -d)"
trap 'rm -rf "$WORKDIR"' EXIT

echo "==> node $(node -v)"
echo "==> Packing"
TARBALL="$WORKDIR/$(cd "$PKG_DIR" && npm pack --pack-destination "$WORKDIR" --silent)"

PROGRAM='const { Rejected, Restricted, CodesToHttp, ProblemConverter, Err } = X;
if (CodesToHttp().toCode(Restricted.UNAUTHORIZED) !== 401) throw new Error("toCode(UNAUTHORIZED) != 401");
const status = Rejected("DUPLICATE_CHARGE", "Duplicate", "stripe.com", "payments.cards");
const code = ProblemConverter().convert(status, Err.onField("amount", "Too low")).code;
if (code !== "stripe.com:payments.cards:Failed:Rejected:DUPLICATE_CHARGE") throw new Error("bad code " + code);'

mkdir "$WORKDIR/esm" "$WORKDIR/cjs"

echo "==> ESM consumer: import"
cd "$WORKDIR/esm"
echo '{"name":"esm-consumer","private":true,"type":"module"}' > package.json
npm install "$TARBALL" >/dev/null
printf 'import * as X from "@kiitdev/codes";\n%s\nconsole.log("ok: import");\n' "$PROGRAM" > check.mjs
node check.mjs

echo "==> CommonJS consumer: require()"
cd "$WORKDIR/cjs"
echo '{"name":"cjs-consumer","private":true}' > package.json
npm install "$TARBALL" >/dev/null
printf 'const X = require("@kiitdev/codes");\n%s\nconsole.log("ok: require");\n' "$PROGRAM" > check.cjs
node --no-warnings check.cjs

echo "==> require() and import() return the same module"
node --no-warnings -e 'const a = require("@kiitdev/codes"); import("@kiitdev/codes").then((b) => { if (a.Restricted !== b.Restricted) { console.error("two copies loaded"); process.exit(1); } console.log("ok: same module"); })'

echo "==> Types: nodenext (.ts and .cts) and bundler"
TYPES='import { Restricted, CodesToHttp, ProblemConverter } from "@kiitdev/codes";
import type { Status } from "@kiitdev/codes";
const status: Status = Restricted.UNAUTHORIZED;
export const http: number | undefined = CodesToHttp().toCode(status);
export const type: string = ProblemConverter().convert(status).type;'
cd "$WORKDIR/esm"
printf '%s\n' "$TYPES" > types.ts
"$TSC" --noEmit --strict --target ES2022 --module nodenext types.ts
"$TSC" --noEmit --strict --target ES2022 --module esnext --moduleResolution bundler types.ts
cd "$WORKDIR/cjs"
printf '%s\n' "$TYPES" > types.cts
"$TSC" --noEmit --strict --target ES2022 --module nodenext types.cts

echo "==> Consumer check passed"
