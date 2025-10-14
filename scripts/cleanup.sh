#!/bin/bash

set -e

CLUSTER_NAME="lb-demo"

echo "================================"
echo "Cleaning up Kind Cluster"
echo "================================"

if kind get clusters | grep -q "^${CLUSTER_NAME}$"; then
    echo "Deleting cluster: ${CLUSTER_NAME}"
    kind delete cluster --name ${CLUSTER_NAME}
    echo "✓ Cluster deleted"
else
    echo "Cluster '${CLUSTER_NAME}' not found"
fi

echo ""
echo "Cleanup complete!"

