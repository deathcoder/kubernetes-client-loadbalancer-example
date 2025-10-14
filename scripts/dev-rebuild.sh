#!/bin/bash

# Quick rebuild and redeploy script for faster development loop
# Usage: ./scripts/dev-rebuild.sh [sample-service|client-service|all]

set -e

CLUSTER_NAME="lb-demo"
NAMESPACE="lb-demo"
SERVICE=${1:-all}

echo "================================"
echo "Quick Rebuild and Redeploy"
echo "Service: ${SERVICE}"
echo "================================"

rebuild_service() {
    local service_name=$1
    echo ""
    echo "Rebuilding ${service_name}..."
    
    # Build only the specified service
    mvn clean package -DskipTests -pl ${service_name} -am
    
    # Build Docker image
    docker build -t ${service_name}:latest ./${service_name}
    
    # Load into Kind
    kind load docker-image ${service_name}:latest --name ${CLUSTER_NAME}
    
    # Restart deployments
    echo "Restarting ${service_name} deployments..."
    kubectl rollout restart deployment -n ${NAMESPACE} -l app=${service_name}
    kubectl rollout status deployment -n ${NAMESPACE} -l app=${service_name} --timeout=60s
    
    echo "✓ ${service_name} rebuilt and redeployed"
}

case ${SERVICE} in
    sample-service)
        rebuild_service "sample-service"
        ;;
    client-service)
        rebuild_service "client-service"
        ;;
    all)
        rebuild_service "sample-service"
        rebuild_service "client-service"
        ;;
    *)
        echo "Unknown service: ${SERVICE}"
        echo "Usage: $0 [sample-service|client-service|all]"
        exit 1
        ;;
esac

echo ""
echo "================================"
echo "Rebuild Complete!"
echo "================================"
echo ""
kubectl get pods -n ${NAMESPACE} -L zone
echo ""
echo "Ready to test! Run: ./scripts/test-loadbalancing.sh"

