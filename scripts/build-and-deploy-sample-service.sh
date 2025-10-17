#!/bin/bash

set -e

NAMESPACE="lb-demo"

echo "========================================"
echo "Building and Deploying Sample Service"
echo "========================================"

# Build sample-service
echo ""
echo "Building sample-service with Maven..."
mvn clean package -pl sample-service -am -DskipTests

# Build Docker image in Kind's Docker environment
echo ""
echo "Building Docker image..."
docker build -t sample-service:latest sample-service/

# Load image into Kind cluster
echo ""
echo "Loading image into Kind cluster..."
kind load docker-image sample-service:latest --name lb-demo

# Create namespace if it doesn't exist
echo ""
echo "Ensuring namespace exists..."
kubectl apply -f k8s/namespace.yaml

# Create RBAC if it doesn't exist
echo ""
echo "Ensuring RBAC is configured..."
kubectl apply -f k8s/rbac.yaml

# Deploy sample-service
echo ""
echo "Deploying sample-service to Kubernetes..."
kubectl apply -f k8s/sample-service.yaml

# Force restart to pick up new images if already deployed
if kubectl get deployment sample-service-zone-a -n ${NAMESPACE} &> /dev/null; then
    echo ""
    echo "Restarting deployments to use new images..."
    kubectl rollout restart deployment/sample-service-zone-a -n ${NAMESPACE}
    kubectl rollout restart deployment/sample-service-zone-b -n ${NAMESPACE}
fi

# Wait for deployments
echo ""
echo "Waiting for deployments to be ready..."
kubectl wait --for=condition=available --timeout=180s \
  deployment/sample-service-zone-a \
  deployment/sample-service-zone-b \
  -n ${NAMESPACE}

echo ""
echo "========================================"
echo "Sample Service Deployed Successfully!"
echo "========================================"
echo ""
echo "Check deployment status:"
echo "  kubectl get pods -n ${NAMESPACE} -l app=sample-service"
echo ""
echo "View logs:"
echo "  kubectl logs -n ${NAMESPACE} -l app=sample-service,zone=zone-a --tail=50"
echo "  kubectl logs -n ${NAMESPACE} -l app=sample-service,zone=zone-b --tail=50"
echo ""
echo "Test the service:"
echo "  kubectl port-forward -n ${NAMESPACE} svc/sample-service 8080:8080"
echo "  curl http://localhost:8080/info"
echo ""
echo "Next steps:"
echo "  Deploy a client service to test zone-aware load balancing:"
echo "    ./scripts/build-and-deploy.sh              # Custom client"
echo "    ./scripts/build-and-deploy-simple.sh       # Simple client"
echo "    ./scripts/build-and-deploy-slice.sh        # Slice client"
echo "    ./scripts/build-and-deploy-mp-browse.sh    # Your production app"

