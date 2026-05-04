#!/bin/bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
CONF_ROOT="$ROOT/config/luban"
LUBAN_EXAMPLES_ROOT="${LUBAN_EXAMPLES_ROOT:-/tmp/luban_examples}"
LUBAN_DLL="$LUBAN_EXAMPLES_ROOT/Tools/Luban/Luban.dll"
JAVA_CORELIB="$LUBAN_EXAMPLES_ROOT/Projects/Java_bin/src/main/corelib/luban"
OUTPUT_CODE_DIR="$ROOT/common/src/generated/luban/java"
OUTPUT_DATA_DIR="$ROOT/common/src/generated/luban/resources/luban"

if [ ! -f "$LUBAN_DLL" ]; then
  echo "Luban tool not found: $LUBAN_DLL" >&2
  echo "Set LUBAN_EXAMPLES_ROOT or clone https://github.com/focus-creative-games/luban_examples" >&2
  exit 1
fi

if [ ! -d "$JAVA_CORELIB" ]; then
  echo "Luban Java corelib not found: $JAVA_CORELIB" >&2
  exit 1
fi

rm -rf "$OUTPUT_CODE_DIR" "$OUTPUT_DATA_DIR"
mkdir -p "$OUTPUT_CODE_DIR" "$OUTPUT_DATA_DIR"

dotnet "$LUBAN_DLL" \
  -t server \
  -c java-bin \
  -d bin \
  --conf "$CONF_ROOT/luban.conf" \
  -x outputCodeDir="$OUTPUT_CODE_DIR" \
  -x outputDataDir="$OUTPUT_DATA_DIR"

mkdir -p "$OUTPUT_CODE_DIR/luban"
cp "$JAVA_CORELIB"/*.java "$OUTPUT_CODE_DIR/luban/"

echo "Generated Luban Java code into $OUTPUT_CODE_DIR"
echo "Generated Luban binary data into $OUTPUT_DATA_DIR"
