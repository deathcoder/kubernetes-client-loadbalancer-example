#!/bin/bash

set -e

CLUSTER_NAME="lb-demo"

echo "================================"
echo "Building and Deploying Applications"
echo "================================"

# Check if cluster exists
if ! kind get clusters | grep -q "^${CLUSTER_NAME}$"; then
    echo "Cluster '${CLUSTER_NAME}' not found. Please run ./scripts/setup-kind-cluster.sh first"
    exit 1
fi

# Build applications
echo ""
echo "Building applications with Maven..."
mvn clean package -DskipTests

# Build Docker images
echo ""
echo "Building Docker images..."
docker build -t sample-service:latest ./sample-service
docker build -t client-service:latest ./client-service

# Load images into Kind cluster
echo ""
echo "Loading images into Kind cluster..."
kind load docker-image sample-service:latest --name ${CLUSTER_NAME}
kind load docker-image client-service:latest --name ${CLUSTER_NAME}

# Deploy to Kubernetes
echo ""
echo "Deploying to Kubernetes..."
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/rbac.yaml
kubectl apply -f k8s/sample-service.yaml
kubectl apply -f k8s/client-service.yaml

# Force restart to pick up new images
echo ""
echo "Restarting deployments to use new images..."
kubectl rollout restart deployment/sample-service-zone-a -n lb-demo
kubectl rollout restart deployment/sample-service-zone-b -n lb-demo
kubectl rollout restart deployment/client-service-zone-a -n lb-demo
kubectl rollout restart deployment/client-service-zone-b -n lb-demo

# Wait for deployments
echo ""
echo "Waiting for deployments to be ready..."
kubectl wait --for=condition=available --timeout=120s \
  deployment/sample-service-zone-a \
  deployment/sample-service-zone-b \
  deployment/client-service-zone-a \
  deployment/client-service-zone-b \
  -n lb-demo

echo ""
echo "================================"
echo "Deployment Complete!"
echo "================================"
echo ""
echo "Pods status:"
kubectl get pods -n lb-demo -L zone,topology.kubernetes.io/zone -o wide
echo ""
echo "Services:"
kubectl get svc -n lb-demo
echo ""
echo "Next steps:"
echo "  Run ./scripts/test-loadbalancing.sh to test the load balancing"
echo "  Or access the client service directly:"
echo "    kubectl port-forward -n lb-demo svc/client-service 8081:8081"
echo "    Then visit: http://localhost:8081/test-loadbalancing?calls=20"

