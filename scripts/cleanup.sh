#!/bin/bash

# Quick cluster cleanup (no confirmation)
# For interactive cleanup with confirmation, use: ./scripts/destroy-cluster.sh
# For complete cleanup including Docker images, use: ./scripts/cleanup-all.sh

set -e

CLUSTER_NAME="lb-demo"

echo "================================"
echo "Cleaning up Kind Cluster"
echo "================================"

if kind get clusters 2>/dev/null | grep -q "^${CLUSTER_NAME}$"; then
    echo "Deleting cluster: ${CLUSTER_NAME}"
    kind delete cluster --name ${CLUSTER_NAME}
    echo "✓ Cluster deleted"
else
    echo "Cluster '${CLUSTER_NAME}' not found"
fi

# Clean up leftover Kind network if it exists
if docker network ls | grep -q "^kind"; then
    echo "Removing leftover Kind network..."
    docker network rm kind 2>/dev/null || echo "  (Kind network already removed)"
else
    echo "✓ No leftover Kind network"
fi

echo ""
echo "✓ Cleanup complete!"
echo ""
echo "To start fresh:"
echo "  ./scripts/setup-kind-cluster.sh"

