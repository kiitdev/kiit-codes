#!/usr/bin/env bash
set -euo pipefail

# Compiles and runs sample-swift against the KiitCodes.framework. SKIE compiles its generated
# Swift wrapper code (onEnum, the __Sealed enums, etc.) directly into the framework's own
# KiitCodes.swiftmodule at build time — `import KiitCodes` is all a consumer needs, no separate
# SKIE-generated source files to reference or compile.
#
# This references the Gradle build output directly rather than a real published XCFramework + SPM
# package, since that distribution pipeline doesn't exist yet (see README roadmap) — same "point
# at the build output for local verification" approach samples/sample-ts uses.
#
# Run from this directory: ./run.sh

cd "$(dirname "$0")"

FRAMEWORK_DIR="../../kiit-codes-kotlin/kiit-codes/build/bin/iosSimulatorArm64/debugFramework"

if [[ ! -d "$FRAMEWORK_DIR/KiitCodes.framework" ]]; then
  echo "error: $FRAMEWORK_DIR/KiitCodes.framework not found." >&2
  echo "Build it first: (cd ../../kiit-codes-kotlin && ./gradlew :kiit-codes:linkDebugFrameworkIosSimulatorArm64)" >&2
  exit 1
fi

SDK_PATH="$(xcrun --sdk iphonesimulator --show-sdk-path)"

swiftc \
  -sdk "$SDK_PATH" \
  -target arm64-apple-ios16.4-simulator \
  -F "$FRAMEWORK_DIR" \
  -framework KiitCodes \
  -I "$FRAMEWORK_DIR" \
  Sources/sample-swift/main.swift \
  -o /tmp/sample-swift-bin

# Running an iOS-simulator-targeted binary directly on the host (even setting DYLD_ROOT_PATH)
# fails — it needs `dyld_sim`, which is only available inside an actual booted simulator. Boot
# one, run inside it via `simctl spawn`, then shut it back down.
DEVICE="iPhone 14"
BOOTED_BY_US=0
if ! xcrun simctl list devices | grep -q "$DEVICE.*Booted"; then
  xcrun simctl boot "$DEVICE"
  BOOTED_BY_US=1
fi

xcrun simctl spawn "$DEVICE" /tmp/sample-swift-bin
EXIT_CODE=$?

if [[ "$BOOTED_BY_US" == "1" ]]; then
  xcrun simctl shutdown "$DEVICE"
fi

exit $EXIT_CODE
