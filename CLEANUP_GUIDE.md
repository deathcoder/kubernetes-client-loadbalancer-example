# Cleanup Guide

This project provides three cleanup scripts with different levels of thoroughness:

## Quick Reference

| Script | What It Does | Confirmation? | Use When |
|--------|--------------|---------------|----------|
| `cleanup.sh` | Deletes cluster only | ❌ No | Quick cleanup, scripting |
| `destroy-cluster.sh` | Deletes cluster only | ✅ Yes | Safe interactive cleanup |
| `cleanup-all.sh` | Deletes cluster + Docker images | ✅ Yes | Complete cleanup |

---

## 1. Quick Cleanup (No Confirmation)

**Script**: `./scripts/cleanup.sh`

**What it does**:
- Deletes the Kind cluster `lb-demo`
- No confirmation prompt (immediate deletion)

**Use when**:
- You want fast cleanup
- Using in automated scripts
- You're sure you want to delete

**Example**:
```bash
./scripts/cleanup.sh
```

**Output**:
```
================================
Cleaning up Kind Cluster
================================
Deleting cluster: lb-demo
Deleting cluster "lb-demo" ...
✓ Cluster deleted

✓ Cleanup complete!

To start fresh:
  ./scripts/setup-kind-cluster.sh
```

---

## 2. Safe Cluster Cleanup (With Confirmation)

**Script**: `./scripts/destroy-cluster.sh`

**What it does**:
- Checks if cluster exists
- Asks for confirmation before deleting
- Deletes the Kind cluster `lb-demo`

**Use when**:
- Interactive cleanup
- You want to be extra careful
- You might change your mind

**Example**:
```bash
./scripts/destroy-cluster.sh
```

**Output**:
```
========================================
Destroying Kubernetes Cluster
========================================

Found cluster: lb-demo

Are you sure you want to destroy the 'lb-demo' cluster? (y/N): y

Deleting Kind cluster...
Deleting cluster "lb-demo" ...

========================================
Cluster Destroyed Successfully!
========================================

Cleanup complete. To recreate the cluster, run:
  ./scripts/setup-kind-cluster.sh
```

---

## 3. Complete Cleanup (Cluster + Docker Images)

**Script**: `./scripts/cleanup-all.sh`

**What it does**:
- Asks for confirmation
- Deletes the Kind cluster `lb-demo`
- Removes all Docker images for this project:
  - `sample-service:latest`
  - `client-service:latest`
  - `simple-client-service:latest`
  - `slice-client-service:latest`
- Suggests optional Docker system prune

**Use when**:
- You want a complete clean slate
- Freeing up disk space
- Starting completely fresh
- Done with the project for a while

**Example**:
```bash
./scripts/cleanup-all.sh
```

**Output**:
```
========================================
Complete Cleanup
========================================

This will:
  1. Delete the Kind cluster 'lb-demo'
  2. Remove all Docker images for this project

Are you sure you want to proceed? (y/N): y

Step 1: Deleting Kind cluster...
Deleting cluster "lb-demo" ...
✓ Cluster deleted

Step 2: Removing Docker images...
  Removing sample-service:latest...
  Removing client-service:latest...
  Removing simple-client-service:latest...
  Removing slice-client-service:latest...

✓ Docker images cleaned up

========================================
Optional: Docker System Cleanup
========================================

You may want to also clean up unused Docker resources:
  docker system prune -a --volumes

(This removes all unused images, containers, volumes, and networks)

========================================
Cleanup Complete!
========================================

To start fresh, run:
  ./scripts/setup-kind-cluster.sh
  ./scripts/build-and-deploy.sh
```

---

## Optional: Complete Docker Cleanup

After using `cleanup-all.sh`, you can optionally clean up all unused Docker resources:

```bash
docker system prune -a --volumes
```

**Warning**: This removes:
- All unused images (not just this project)
- All stopped containers
- All unused volumes
- All unused networks

Only run this if you're sure you don't need any other Docker resources.

---

## Starting Fresh After Cleanup

After any cleanup, recreate the environment:

```bash
# 1. Create cluster
./scripts/setup-kind-cluster.sh

# 2. Deploy services (choose one or all)
./scripts/build-and-deploy.sh              # Custom client
./scripts/build-and-deploy-simple.sh       # Simple client (recommended)
./scripts/build-and-deploy-slice.sh        # Slice client

# 3. Test
./scripts/test-loadbalancing.sh
```

---

## Troubleshooting

### Cluster won't delete
```bash
# Force delete if stuck
kind delete cluster --name lb-demo

# Check for orphaned clusters
kind get clusters
```

### Cluster won't recreate after deletion
If you get an error like:
```
ERROR: failed to create cluster: command "docker run --name lb-demo-control-plane" ... failed with error: exit status 125
```

This is usually caused by a leftover Kind network. Fix it with:
```bash
# Check for leftover Kind network
docker network ls | grep kind

# Remove the leftover network
docker network rm kind

# Then recreate the cluster
./scripts/setup-kind-cluster.sh
```

**Note**: All cleanup scripts now automatically remove the Kind network to prevent this issue.

### Docker images persist
```bash
# List images
docker images | grep -E "sample-service|client-service|slice-client"

# Force remove
docker rmi -f sample-service:latest client-service:latest \
  simple-client-service:latest slice-client-service:latest
```

### Out of disk space
```bash
# Check Docker disk usage
docker system df

# Clean up everything (careful!)
docker system prune -a --volumes

# Remove only old images
docker image prune -a
```

---

## Quick Command Reference

```bash
# Quick cleanup (no confirmation)
./scripts/cleanup.sh

# Safe cleanup (with confirmation)
./scripts/destroy-cluster.sh

# Complete cleanup (cluster + images)
./scripts/cleanup-all.sh

# Check what exists
kind get clusters
docker images | grep -E "sample-service|client-service"

# Start fresh
./scripts/setup-kind-cluster.sh
./scripts/build-and-deploy-simple.sh
./scripts/test-loadbalancing.sh
```

