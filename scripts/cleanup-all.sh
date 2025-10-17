#!/bin/bash

# Exit immediately if a command exits with a non-zero status.
set -e

CLUSTER_NAME="lb-demo"

echo "========================================"
echo "Complete Cleanup"
echo "========================================"
echo ""
echo "This will:"
echo "  1. Delete the Kind cluster '${CLUSTER_NAME}'"
echo "  2. Remove all Docker images for this project"
echo ""

# Ask for confirmation
read -p "Are you sure you want to proceed? (y/N): " -n 1 -r
echo
echo

if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Cleanup cancelled."
    exit 0
fi

# Step 1: Delete Kind cluster
echo "Step 1: Deleting Kind cluster..."
if kind get clusters 2>/dev/null | grep -q "^${CLUSTER_NAME}$"; then
    kind delete cluster --name ${CLUSTER_NAME}
    echo "✓ Cluster deleted"
else
    echo "✓ Cluster '${CLUSTER_NAME}' does not exist (skipping)"
fi

# Clean up leftover Kind network
if docker network ls | grep -q "kind"; then
    echo "Removing leftover Kind network..."
    docker network rm kind 2>/dev/null && echo "✓ Kind network removed" || echo "✓ Already removed"
else
    echo "✓ No leftover Kind network"
fi
echo ""

# Step 2: Remove Docker images
echo "Step 2: Removing Docker images..."

IMAGES=(
    "sample-service:latest"
    "client-service:latest"
    "simple-client-service:latest"
    "slice-client-service:latest"
)

for image in "${IMAGES[@]}"; do
    if docker images --format "{{.Repository}}:{{.Tag}}" | grep -q "^${image}$"; then
        echo "  Removing ${image}..."
        docker rmi ${image} 2>/dev/null || echo "    (already removed)"
    else
        echo "  ${image} not found (skipping)"
    fi
done

echo ""
echo "✓ Docker images cleaned up"
echo ""

# Optional: Prune unused Docker resources
echo "========================================"
echo "Optional: Docker System Cleanup"
echo "========================================"
echo ""
echo "You may want to also clean up unused Docker resources:"
echo "  docker system prune -a --volumes"
echo ""
echo "(This removes all unused images, containers, volumes, and networks)"
echo ""

echo "========================================"
echo "Cleanup Complete!"
echo "========================================"
echo ""
echo "To start fresh, run:"
echo "  ./scripts/setup-kind-cluster.sh"
echo "  ./scripts/build-and-deploy.sh"

