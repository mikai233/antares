#!/usr/bin/env sh
set -eu

modules="${MODULES:-player world global gate gm tools}"
image_prefix="${IMAGE_PREFIX:-akka-game-server}"
registry="${REGISTRY:-}"
push="${PUSH:-false}"

project_version() {
  awk -F= '
    /^[[:space:]]*projectVersion[[:space:]]*=/ {
      value = $2
      sub(/^[[:space:]]*/, "", value)
      sub(/[[:space:]]*$/, "", value)
      print value
      exit
    }
  ' gradle.properties 2>/dev/null || true
}

tag="${TAG:-$(project_version)}"
tag="${tag:-latest}"

image_name() {
  module="$1"
  name="${image_prefix}-${module}:${tag}"
  if [ -n "$registry" ]; then
    name="${registry%/}/${name}"
  fi
  echo "$name"
}

for module in $modules; do
  image="$(image_name "$module")"
  echo "Building ${image}"
  docker build \
    --build-arg MODULE="$module" \
    -t "$image" \
    .

  if [ "$push" = "true" ]; then
    echo "Pushing ${image}"
    docker push "$image"
  fi
done
