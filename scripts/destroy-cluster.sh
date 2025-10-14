#!/bin/bash

# Exit immediately if a command exits with a non-zero status.
set -e

CLUSTER_NAME="lb-demo"

echo "========================================"
echo "Destroying Kubernetes Cluster"
echo "========================================"

# Check if the cluster exists
if kind get clusters 2>/dev/null | grep -q "^${CLUSTER_NAME}$"; then
    echo ""
    echo "Found cluster: ${CLUSTER_NAME}"
    echo ""
    
    # Ask for confirmation
    read -p "Are you sure you want to destroy the '${CLUSTER_NAME}' cluster? (y/N): " -n 1 -r
    echo
    
    if [[ $REPLY =~ ^[Yy]$ ]]; then
        echo ""
        echo "Deleting Kind cluster..."
        kind delete cluster --name ${CLUSTER_NAME}
        
        echo ""
        echo "========================================"
        echo "Cluster Destroyed Successfully!"
        echo "========================================"
        
        echo ""
        echo "Cleanup complete. To recreate the cluster, run:"
        echo "  ./scripts/setup-kind-cluster.sh"
    else
        echo ""
        echo "Cluster deletion cancelled."
        exit 0
    fi
else
    echo ""
    echo "Cluster '${CLUSTER_NAME}' does not exist."
    echo ""
    echo "Available clusters:"
    kind get clusters 2>/dev/null || echo "  (none)"
    exit 1
fi

