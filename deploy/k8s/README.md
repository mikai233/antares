# Kubernetes deployment skeleton

This directory contains a first-pass Kubernetes deployment for the game server.

The runtime keeps Zookeeper as the config center, but cluster discovery is switched to
Kubernetes by setting `CLUSTER_DISCOVERY=kubernetes`.

## Build images

```bash
deploy/docker/build-images.sh
```

By default, image tags are read from `projectVersion` in the root `gradle.properties`. The image builder accepts
environment overrides:

```bash
TAG=dev deploy/docker/build-images.sh
REGISTRY=registry.example.com/game TAG=2026.05.05 PUSH=true deploy/docker/build-images.sh
MODULES="gate gm" TAG=debug deploy/docker/build-images.sh
```

`stardust` is intentionally not built as a deployment image. It is a local
development launcher. Production and shared environments should run node roles
as separate containers, even if those containers are scheduled onto the same
machine.

## Deployment shape

Production Kubernetes and shared internal environments should run each node role
as a separate container: `gate`, `player`, `world`, `global`, and `gm`.

`stardust` is only for local development. It starts multiple nodes in one JVM to
make debugging cheaper, but it is not a production or shared-environment runtime
shape.

## JVM options

Common environment variables live in `config.yaml`. Put values there only when
they are shared by every node role, such as Zookeeper, MongoDB, cluster
discovery, and runtime config initialization.

Node-specific environment variables belong in `game-server.yaml` on each
Deployment. `JAVA_OPTS` is node-specific because memory pressure differs by
role. The current base values are:

```text
player  -Xms512m -Xmx1536m -XX:+ExitOnOutOfMemoryError
world   -Xms512m -Xmx1536m -XX:+ExitOnOutOfMemoryError
gate    -Xms256m -Xmx768m  -XX:+ExitOnOutOfMemoryError
global  -Xms256m -Xmx768m  -XX:+ExitOnOutOfMemoryError
gm      -Xms128m -Xmx384m  -XX:+ExitOnOutOfMemoryError
```

`deploy/docker/entrypoint.sh` consumes `JAVA_OPTS` before starting the selected
module jar.

For smaller internal development clusters, use the dev overlay:

```bash
kubectl apply -k deploy/k8s-dev
```

The dev overlay currently overrides each node role:

```text
player  -Xms128m -Xmx384m -XX:+ExitOnOutOfMemoryError
world   -Xms128m -Xmx384m -XX:+ExitOnOutOfMemoryError
gate    -Xms96m  -Xmx256m -XX:+ExitOnOutOfMemoryError
global  -Xms96m  -Xmx256m -XX:+ExitOnOutOfMemoryError
gm      -Xms64m  -Xmx192m -XX:+ExitOnOutOfMemoryError
```

For production, keep JVM heap and Kubernetes memory limits aligned per node
role. Player and world usually need the largest heap, while GM and global can
start smaller.

## Shutdown

Kubernetes manifests use rolling updates with `maxUnavailable=0`, per-role
resource limits, a 180 second termination grace period, and a short `preStop`
delay. The delay gives Kubernetes time to remove the terminating Pod from
service endpoints before the JVM receives `SIGTERM`.

After `SIGTERM`, nodes rely on Pekko coordinated shutdown. Gate nodes also enter
connection drain mode: new sessions are rejected and existing sessions are asked
to close before the gateway transport stops.

`PRE_STOP_DRAIN_SECONDS` controls the pre-stop endpoint drain delay. Keep this
well below `terminationGracePeriodSeconds`, because Kubernetes counts pre-stop
time as part of the total termination grace period.

## Apply

```bash
kubectl apply -k deploy/k8s
```

The runtime config Job initializes MongoDB, gate Netty, and game-world runtime
config. Game config publication data still needs to be published through the
project's Luban config flow before production startup.

Common runtime environment is kept in `config.yaml`. Production deployments
should replace the bundled Zookeeper and MongoDB manifests with managed
services or StatefulSets with persistent volumes before carrying real data.
