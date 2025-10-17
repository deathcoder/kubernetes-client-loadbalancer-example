#!/bin/bash

set -e

echo "================================"
echo "Building and Deploying Wrapped Client"
echo "================================"

# Build the Maven project
echo ""
echo "Building wrapped-client-service with Maven..."
mvn clean package -pl wrapped-client-service -am -DskipTests

# Build Docker image
echo ""
echo "Building Docker image..."
docker build -t wrapped-client-service:latest -f wrapped-client-service/Dockerfile wrapped-client-service/

# Load image into Kind cluster
echo ""
echo "Loading image into Kind cluster..."
kind load docker-image wrapped-client-service:latest --name lb-demo

# Deploy to Kubernetes
echo ""
echo "Deploying to Kubernetes..."
kubectl apply -f k8s/wrapped-client-service.yaml

# Restart deployments to use new images
echo ""
echo "Restarting deployments to use new images..."
kubectl rollout restart deployment/wrapped-client-service-zone-a -n lb-demo
kubectl rollout restart deployment/wrapped-client-service-zone-b -n lb-demo

# Wait for deployments to be ready
echo ""
echo "Waiting for deployments to be ready..."
kubectl rollout status deployment/wrapped-client-service-zone-a -n lb-demo --timeout=120s
kubectl rollout status deployment/wrapped-client-service-zone-b -n lb-demo --timeout=120s

echo ""
echo "================================"
echo "Deployment Complete!"
echo "================================"
echo ""
echo "Pods status:"
kubectl get pods -n lb-demo -l app=wrapped-client-service -o wide

echo ""
echo "Next steps:"
echo "  Check logs: kubectl logs -n lb-demo -l app=wrapped-client-service --tail=100"
echo "  Test: kubectl exec -n lb-demo <pod-name> -- curl -s http://localhost:8081/test-loadbalancing?calls=10"

