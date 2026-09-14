#!/usr/bin/env bash
set -euo pipefail

# Publishes @kiit/codes to npm from the native TypeScript port (ports/kiit-codes-ts). Run from the
# repository root:
#
#   npm login          # one-time, if not already authenticated
#   ./scripts/publish-npm.sh

cd "$(dirname "$0")/../ports/kiit-codes-ts"

echo "==> Installing dependencies"
npm ci

echo "==> Running typecheck and tests"
npm run typecheck
npm test

PACKAGE_NAME="$(node -p "require('./package.json').name")"
PACKAGE_VERSION="$(node -p "require('./package.json').version")"

echo "==> Contents to be published ($PACKAGE_NAME@$PACKAGE_VERSION):"
npm run build
npm pack --dry-run

read -rp "Publish $PACKAGE_NAME@$PACKAGE_VERSION to npm? [y/N] " CONFIRM
if [[ "$CONFIRM" != "y" && "$CONFIRM" != "Y" ]]; then
  echo "aborted"
  exit 1
fi

# --access public is required the first time a new scoped (@kiit/...) package is published —
# scoped packages default to private on npm otherwise. npm publish re-runs prepublishOnly
# (typecheck + test + build) automatically before publishing.
npm publish --access public
