#!/bin/bash

set -e

NAMESPACE="lb-demo"

echo "========================================"
echo "Cleaning Up All Pods"
echo "========================================"

# Check if namespace exists
if ! kubectl get namespace ${NAMESPACE} &> /dev/null; then
    echo "Namespace '${NAMESPACE}' does not exist. Nothing to clean up."
    exit 0
fi

echo ""
echo "Current pods in ${NAMESPACE}:"
kubectl get pods -n ${NAMESPACE}

echo ""
read -p "Are you sure you want to delete all pods in ${NAMESPACE}? (y/N): " -n 1 -r
echo
echo

if [[ ! $REPLY =~ ^[Yy]$ ]]; then
    echo "Cleanup cancelled."
    exit 0
fi

echo "Deleting all deployments..."

# List of all possible deployments
DEPLOYMENTS=(
    "sample-service-zone-a"
    "sample-service-zone-b"
    "client-service-zone-a"
    "client-service-zone-b"
    "simple-client-service-zone-a"
    "simple-client-service-zone-b"
    "slice-client-service-zone-a"
    "slice-client-service-zone-b"
    "mp-browse-zone-a"
    "mp-browse-zone-b"
)

for deployment in "${DEPLOYMENTS[@]}"; do
    if kubectl get deployment ${deployment} -n ${NAMESPACE} &> /dev/null; then
        echo "  Deleting ${deployment}..."
        kubectl delete deployment ${deployment} -n ${NAMESPACE}
    fi
done

echo ""
echo "Deleting all services (except Kubernetes system services)..."

# List of all possible services
SERVICES=(
    "sample-service"
    "client-service"
    "simple-client-service"
    "slice-client-service"
    "mp-browse"
)

for service in "${SERVICES[@]}"; do
    if kubectl get service ${service} -n ${NAMESPACE} &> /dev/null; then
        echo "  Deleting ${service}..."
        kubectl delete service ${service} -n ${NAMESPACE}
    fi
done

echo ""
echo "========================================"
echo "All Pods Cleaned Up!"
echo "========================================"
echo ""
echo "Remaining resources in ${NAMESPACE}:"
kubectl get all -n ${NAMESPACE}
echo ""
echo "To redeploy:"
echo "  ./scripts/build-and-deploy-sample-service.sh  # Deploy only sample-service"
echo "  ./scripts/build-and-deploy.sh                 # Deploy custom client"
echo "  ./scripts/build-and-deploy-simple.sh          # Deploy simple client"
echo "  ./scripts/build-and-deploy-slice.sh           # Deploy slice client"
echo "  ./scripts/build-and-deploy-mp-browse.sh       # Deploy mp-browse"

