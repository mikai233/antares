# Kubernetes deployment skeleton

This directory contains a first-pass Kubernetes deployment for the game server.

The runtime keeps Zookeeper as the config center, but cluster discovery is switched to
Kubernetes by setting `CLUSTER_DISCOVERY=kubernetes`.

## Build images

```bash
docker build --build-arg MODULE=player -t akka-game-server-player:latest .
docker build --build-arg MODULE=world -t akka-game-server-world:latest .
docker build --build-arg MODULE=global -t akka-game-server-global:latest .
docker build --build-arg MODULE=gate -t akka-game-server-gate:latest .
docker build --build-arg MODULE=gm -t akka-game-server-gm:latest .
docker build --build-arg MODULE=tools -t akka-game-server-tools:latest .
```

## Apply

```bash
kubectl apply -f deploy/k8s/namespace.yaml
kubectl apply -f deploy/k8s/rbac.yaml
kubectl apply -f deploy/k8s/infrastructure.yaml
kubectl apply -f deploy/k8s/runtime-config-job.yaml
kubectl apply -f deploy/k8s/game-server.yaml
```

The runtime config Job initializes MongoDB, gate Netty, and game-world runtime
config. Game config publication data still needs to be published through the
project's Luban config flow before production startup.
