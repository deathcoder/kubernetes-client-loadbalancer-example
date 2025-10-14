#!/bin/bash

set -e

NAMESPACE="lb-demo"

echo "================================"
echo "Testing Zone-Aware Load Balancing"
echo "================================"

# Check if deployments are ready
if ! kubectl get deployment client-service-zone-a -n ${NAMESPACE} &> /dev/null; then
    echo "Deployments not found. Please run ./scripts/build-and-deploy.sh first"
    exit 1
fi

echo ""
echo "Current pod distribution:"
kubectl get pods -n ${NAMESPACE} -L zone,topology.kubernetes.io/zone -o wide
echo ""

# Test from zone-a client
echo "================================"
echo "Testing from zone-a client"
echo "================================"
ZONE_A_POD=$(kubectl get pod -n ${NAMESPACE} -l app=client-service,zone=zone-a -o jsonpath='{.items[0].metadata.name}')

echo "Client pod: ${ZONE_A_POD}"
echo ""
echo "Making 20 calls to sample-service..."
kubectl exec -n ${NAMESPACE} ${ZONE_A_POD} -- curl -s http://localhost:8081/test-loadbalancing?calls=20 | jq '.'

echo ""
echo "================================"
echo "Testing from zone-b client"
echo "================================"
ZONE_B_POD=$(kubectl get pod -n ${NAMESPACE} -l app=client-service,zone=zone-b -o jsonpath='{.items[0].metadata.name}')

echo "Client pod: ${ZONE_B_POD}"
echo ""
echo "Making 20 calls to sample-service..."
kubectl exec -n ${NAMESPACE} ${ZONE_B_POD} -- curl -s http://localhost:8081/test-loadbalancing?calls=20 | jq '.'

echo ""
echo "================================"
echo "Test Summary"
echo "================================"
echo "✓ If zone-aware load balancing is working correctly:"
echo "  - zone-a client should primarily call zone-a service pods"
echo "  - zone-b client should primarily call zone-b service pods"
echo "  - Same-zone percentage should be close to 100%"
echo ""
echo "You can also access the test endpoint directly:"
echo "  kubectl port-forward -n ${NAMESPACE} svc/client-service 8081:8081"
echo "  Then visit: http://localhost:8081/test-loadbalancing?calls=50"

