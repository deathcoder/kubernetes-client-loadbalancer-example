#!/bin/bash

# Port forward to access services locally
# Usage: ./scripts/port-forward.sh [client-service|sample-service] [zone-a|zone-b]

NAMESPACE="lb-demo"
SERVICE=${1:-client-service}
ZONE=${2:-zone-a}

if [ "${SERVICE}" == "client-service" ]; then
    PORT=8081
else
    PORT=8080
fi

POD=$(kubectl get pod -n ${NAMESPACE} -l app=${SERVICE},zone=${ZONE} -o jsonpath='{.items[0].metadata.name}')

if [ -z "${POD}" ]; then
    echo "No pod found for service=${SERVICE}, zone=${ZONE}"
    exit 1
fi

echo "Port forwarding to ${POD} (${SERVICE} in ${ZONE})"
echo "Access at: http://localhost:${PORT}"
echo ""

if [ "${SERVICE}" == "client-service" ]; then
    echo "Test endpoints:"
    echo "  http://localhost:${PORT}/client-info"
    echo "  http://localhost:${PORT}/call-service"
    echo "  http://localhost:${PORT}/test-loadbalancing?calls=20"
else
    echo "Test endpoints:"
    echo "  http://localhost:${PORT}/info"
    echo "  http://localhost:${PORT}/health"
fi

echo ""
echo "Press Ctrl+C to stop"
echo ""

kubectl port-forward -n ${NAMESPACE} ${POD} ${PORT}:${PORT}

