#!/bin/bash

set -e

echo "================================"
echo "Setting up Kind Cluster with Zone Simulation"
echo "================================"

# Check if kind is installed
if ! command -v kind &> /dev/null; then
    echo "Kind is not installed. Please install it first:"
    echo "  brew install kind"
    exit 1
fi

# Check if kubectl is installed
if ! command -v kubectl &> /dev/null; then
    echo "kubectl is not installed. Please install it first:"
    echo "  brew install kubectl"
    exit 1
fi

CLUSTER_NAME="lb-demo"

# Delete existing cluster if it exists
if kind get clusters | grep -q "^${CLUSTER_NAME}$"; then
    echo "Deleting existing cluster: ${CLUSTER_NAME}"
    kind delete cluster --name ${CLUSTER_NAME}
fi

# Create Kind cluster with multiple nodes
echo "Creating Kind cluster with multiple nodes..."
cat <<EOF | kind create cluster --name ${CLUSTER_NAME} --config=-
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
nodes:
  - role: control-plane
  - role: worker
  - role: worker
  - role: worker
EOF

echo "Waiting for cluster to be ready..."
kubectl wait --for=condition=Ready nodes --all --timeout=60s

# Label nodes with zones to simulate multi-zone deployment
echo "Labeling nodes with zone information..."
WORKERS=($(kubectl get nodes -o name | grep worker))

# Assign zone-a to first two workers
kubectl label node ${WORKERS[0]#node/} topology.kubernetes.io/zone=zone-a --overwrite
kubectl label node ${WORKERS[1]#node/} topology.kubernetes.io/zone=zone-a --overwrite

# Assign zone-b to remaining worker(s)
kubectl label node ${WORKERS[2]#node/} topology.kubernetes.io/zone=zone-b --overwrite

echo ""
echo "================================"
echo "Cluster Setup Complete!"
echo "================================"
echo ""
echo "Node topology:"
kubectl get nodes -L topology.kubernetes.io/zone
echo ""
echo "Next steps:"
echo "  1. Run ./scripts/build-and-deploy.sh to build and deploy applications"
echo "  2. Run ./scripts/test-loadbalancing.sh to test zone-aware load balancing"

