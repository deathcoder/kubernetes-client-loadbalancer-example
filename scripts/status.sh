#!/bin/bash

# Show current status of the demo environment

NAMESPACE="lb-demo"
CLUSTER_NAME="lb-demo"

echo "================================"
echo "Kubernetes LoadBalancer Demo Status"
echo "================================"
echo ""

# Check if cluster exists
if ! kind get clusters 2>/dev/null | grep -q "^${CLUSTER_NAME}$"; then
    echo "❌ Cluster '${CLUSTER_NAME}' not found"
    echo ""
    echo "Run: ./scripts/setup-kind-cluster.sh"
    exit 1
else
    echo "✅ Cluster '${CLUSTER_NAME}' is running"
fi

# Check if namespace exists
if ! kubectl get namespace ${NAMESPACE} &> /dev/null; then
    echo "❌ Namespace '${NAMESPACE}' not found"
    echo ""
    echo "Run: ./scripts/build-and-deploy.sh"
    exit 1
else
    echo "✅ Namespace '${NAMESPACE}' exists"
fi

echo ""
echo "================================"
echo "Nodes and Zones"
echo "================================"
kubectl get nodes -L topology.kubernetes.io/zone

echo ""
echo "================================"
echo "Pods Status"
echo "================================"
kubectl get pods -n ${NAMESPACE} -L zone,topology.kubernetes.io/zone -o wide

echo ""
echo "================================"
echo "Services"
echo "================================"
kubectl get svc -n ${NAMESPACE}

echo ""
echo "================================"
echo "Quick Actions"
echo "================================"
echo "  Test load balancing:  ./scripts/test-loadbalancing.sh"
echo "  View logs:            ./scripts/logs.sh [service] [zone]"
echo "  Port forward:         ./scripts/port-forward.sh [service] [zone]"
echo "  Rebuild service:      ./scripts/dev-rebuild.sh [service]"
echo ""

