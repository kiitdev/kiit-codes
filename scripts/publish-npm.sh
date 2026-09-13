#!/usr/bin/env bash
set -euo pipefail

# Publishes @kiit/codes to npm from the Kotlin/JS production library distribution.
#
# Local-only — deliberately NOT wired into .github/workflows/release.yml yet. That's a separate,
# tracked follow-up (see the README roadmap). Run from the repository root:
#
#   npm login          # one-time, if not already authenticated
#   ./scripts/publish-npm.sh

cd "$(dirname "$0")/../kiit-codes-kotlin"

echo "==> Building the JS production library distribution"
./gradlew :kiit-codes:jsBrowserProductionLibraryDistribution

DIST_DIR="kiit-codes/build/dist/js/productionLibrary"
if [[ ! -f "$DIST_DIR/package.json" ]]; then
  echo "error: $DIST_DIR/package.json not found — did the Gradle task name change?" >&2
  exit 1
fi

PACKAGE_NAME="$(node -p "require('./$DIST_DIR/package.json').name")"
PACKAGE_VERSION="$(node -p "require('./$DIST_DIR/package.json').version")"

echo "==> Contents to be published ($PACKAGE_NAME@$PACKAGE_VERSION):"
(cd "$DIST_DIR" && npm pack --dry-run)

read -rp "Publish $PACKAGE_NAME@$PACKAGE_VERSION to npm? [y/N] " CONFIRM
if [[ "$CONFIRM" != "y" && "$CONFIRM" != "Y" ]]; then
  echo "aborted"
  exit 1
fi

# --access public is required the first time a new scoped (@kiit/...) package is published —
# scoped packages default to private on npm otherwise.
(cd "$DIST_DIR" && npm publish --access public)
