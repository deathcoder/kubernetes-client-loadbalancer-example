#!/bin/bash

# View logs from services
# Usage: ./scripts/logs.sh [sample-service|client-service] [zone-a|zone-b]

NAMESPACE="lb-demo"
SERVICE=${1:-client-service}
ZONE=${2:-zone-a}

POD=$(kubectl get pod -n ${NAMESPACE} -l app=${SERVICE},zone=${ZONE} -o jsonpath='{.items[0].metadata.name}')

if [ -z "${POD}" ]; then
    echo "No pod found for service=${SERVICE}, zone=${ZONE}"
    exit 1
fi

echo "Following logs for ${POD}..."
echo "Press Ctrl+C to stop"
echo ""

kubectl logs -n ${NAMESPACE} -f ${POD}

