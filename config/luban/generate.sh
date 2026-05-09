#!/bin/bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
CONF_ROOT="$ROOT/config/luban"
DATA_DIR="${LUBAN_DATA_DIR:-$CONF_ROOT/Datas}"
CONF_FILE="${LUBAN_CONF:-$CONF_ROOT/luban_server.conf}"
if [ -z "${LUBAN_TOOL_ROOT:-}" ]; then
  echo "Luban tool root is not configured. Set LUBAN_TOOL_ROOT." >&2
  exit 1
fi
LUBAN_DLL="$LUBAN_TOOL_ROOT/Tools/Luban/Luban.dll"
JAVA_CORELIB="$LUBAN_TOOL_ROOT/Projects/Java_bin/src/main/corelib/luban"
OUTPUT_CODE_DIR="$ROOT/common/src/generated/luban/java"
OUTPUT_DATA_DIR="$ROOT/common/build/generated/luban/resources/luban"

if [ ! -f "$LUBAN_DLL" ]; then
  echo "Luban tool not found: $LUBAN_DLL" >&2
  echo "Set LUBAN_TOOL_ROOT to a Luban checkout that contains Tools/Luban/Luban.dll" >&2
  exit 1
fi

if [ ! -d "$JAVA_CORELIB" ]; then
  echo "Luban Java corelib not found: $JAVA_CORELIB" >&2
  exit 1
fi

if [ ! -d "$DATA_DIR" ]; then
  echo "Luban data dir not found: $DATA_DIR" >&2
  exit 1
fi

if [ ! -f "$CONF_FILE" ]; then
  echo "Luban conf file not found: $CONF_FILE" >&2
  exit 1
fi

rm -rf "$OUTPUT_CODE_DIR" "$OUTPUT_DATA_DIR"
mkdir -p "$OUTPUT_CODE_DIR" "$OUTPUT_DATA_DIR"

dotnet "$LUBAN_DLL" \
  -t server \
  -c java-bin \
  -d bin \
  --conf "$CONF_FILE" \
  -x inputDataDir="$DATA_DIR" \
  -x outputCodeDir="$OUTPUT_CODE_DIR" \
  -x outputDataDir="$OUTPUT_DATA_DIR"

mkdir -p "$OUTPUT_CODE_DIR/luban"
cp "$JAVA_CORELIB"/*.java "$OUTPUT_CODE_DIR/luban/"

while IFS= read -r -d '' file; do
  package_line="$(grep -m1 '^package ' "$file" || true)"
  if [ -z "$package_line" ]; then
    continue
  fi
  package_name="${package_line#package }"
  package_name="${package_name%;}"
  package_path="$(printf '%s' "$package_name" | tr '.' '/')"
  target_dir="$OUTPUT_CODE_DIR/$package_path"
  mkdir -p "$target_dir"
  target_file="$target_dir/$(basename "$file")"
  if [ "$file" != "$target_file" ]; then
    mv "$file" "$target_file"
  fi
done < <(find "$OUTPUT_CODE_DIR" -type f -name '*.java' ! -path "$OUTPUT_CODE_DIR/luban/*" -print0)

echo "Generated Luban Java code into $OUTPUT_CODE_DIR"
echo "Generated Luban binary data into $OUTPUT_DATA_DIR"
echo "Using Luban Excel data dir $DATA_DIR"
echo "Using Luban conf file $CONF_FILE"
