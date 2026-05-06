#!/usr/bin/env sh
set -eu

module="${APP_MODULE:?APP_MODULE is required}"

default_port() {
  case "$module" in
    player) echo "2333" ;;
    gate) echo "2334" ;;
    global) echo "2335" ;;
    world) echo "2336" ;;
    gm) echo "2337" ;;
    tools) echo "0" ;;
    *) echo "2330" ;;
  esac
}

if [ "$module" = "tools" ]; then
  exec java ${JAVA_OPTS:-} -jar /app/app.jar "$@"
fi

node_port="${NODE_PORT:-$(default_port)}"
node_host="${NODE_HOST:-${POD_IP:-0.0.0.0}}"
node_id="${NODE_ID:-${module}-${POD_NAME:-${HOSTNAME:-$node_port}}}"
node_conf="${NODE_CONF:-${module}.conf}"
zookeeper_connect="${ZOOKEEPER_CONNECT:-zookeeper:2181}"
system_name="${SYSTEM_NAME:-antares}"

exec java ${JAVA_OPTS:-} -jar /app/app.jar \
  --host "$node_host" \
  --port "$node_port" \
  --conf "$node_conf" \
  --zookeeper "$zookeeper_connect" \
  --name "$system_name" \
  --node-id "$node_id" \
  "$@"
