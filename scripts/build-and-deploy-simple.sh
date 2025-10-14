#!/bin/bash
set -e

echo "================================"
echo "Building and Deploying Simple Client"
echo "================================"
echo ""

# Build with Maven
echo "Building simple-client-service with Maven..."
mvn clean package -DskipTests -pl simple-client-service -am

# Build Docker image
echo ""
echo "Building Docker image..."
docker build -t simple-client-service:latest -f simple-client-service/Dockerfile simple-client-service

# Load image into Kind cluster
echo ""
echo "Loading image into Kind cluster..."
kind load docker-image simple-client-service:latest --name lb-demo

# Deploy to Kubernetes
echo ""
echo "Deploying to Kubernetes..."
kubectl apply -f k8s/simple-client-service.yaml

# Restart deployments to pick up new image
echo ""
echo "Restarting deployments to use new images..."
kubectl rollout restart deployment/simple-client-service-zone-a deployment/simple-client-service-zone-b -n lb-demo

# Wait for deployments to be ready
echo ""
echo "Waiting for deployments to be ready..."
kubectl wait --for=condition=available --timeout=60s \
  deployment/simple-client-service-zone-a \
  deployment/simple-client-service-zone-b \
  -n lb-demo

echo ""
echo "================================"
echo "Deployment Complete!"
echo "================================"
echo ""

# Show pod status
echo "Pods status:"
kubectl get pods -n lb-demo -l app=simple-client-service -o wide \
  --show-labels

echo ""
echo "Next steps:"
echo "  Check logs: kubectl logs -n lb-demo -l app=simple-client-service --tail=100"
echo "  Test: kubectl exec -n lb-demo <pod-name> -- curl -s http://localhost:8081/test-loadbalancing?calls=10"

