#!/usr/bin/env bash
set -euo pipefail

# Installs @kiitdev/codes from the real npm registry into a scratch project and exercises it as
# an actual consumer would: import from node_modules (not source), run a few real calls, and
# type-check against the published .d.ts. Catches packaging/registry-level mistakes that the
# library's own source tests (ports/kiit-codes-ts) can't, since those run against source directly.
#
# The test files live in scripts/smoke-test-npm/ (test.mjs, test-types.ts) — edit those directly
# to add more checks, this script just copies them into a scratch dir and runs them.
#
#   ./scripts/smoke-test-npm.sh          # tests the "latest" published version
#   ./scripts/smoke-test-npm.sh 0.9.0    # tests a specific version

VERSION="${1:-latest}"
TS_VERSION="5.9.3" # matches ports/kiit-codes-ts's own devDependency pin
FIXTURES_DIR="$(cd "$(dirname "$0")/smoke-test-npm" && pwd)"

WORKDIR="$(mktemp -d)"
trap 'rm -rf "$WORKDIR"' EXIT

cp "$FIXTURES_DIR/test.mjs" "$FIXTURES_DIR/test-types.ts" "$WORKDIR/"
cd "$WORKDIR"
npm init -y >/dev/null 2>&1

echo "==> Installing @kiitdev/codes@$VERSION from the registry"
npm install "@kiitdev/codes@$VERSION" >/dev/null

INSTALLED_VERSION="$(node -p "require('./node_modules/@kiitdev/codes/package.json').version")"
echo "==> Installed version: $INSTALLED_VERSION"

echo "==> Running JS smoke test (real import from node_modules)"
node test.mjs

echo "==> Type-checking against the published .d.ts"
npm install --no-save "typescript@$TS_VERSION" >/dev/null 2>&1
./node_modules/.bin/tsc --noEmit --strict --module NodeNext --moduleResolution NodeNext --target ES2023 test-types.ts

echo "==> @kiitdev/codes@$INSTALLED_VERSION smoke test passed"
