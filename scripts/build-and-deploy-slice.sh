#!/bin/bash

# Exit immediately if a command exits with a non-zero status.
set -e

echo "================================"
echo "Building and Deploying Slice Client"
echo "================================"

# 1. Build the slice-client-service JAR
echo -e "\nBuilding slice-client-service with Maven..."
mvn clean package -pl slice-client-service -am -DskipTests

# 2. Build the Docker image for slice-client-service
echo -e "\nBuilding Docker image..."
docker build -f slice-client-service/Dockerfile -t slice-client-service:latest slice-client-service

# 3. Load the image into Kind cluster
echo -e "\nLoading image into Kind cluster..."
kind load docker-image slice-client-service:latest --name lb-demo

# 4. Deploy to Kubernetes
echo -e "\nDeploying to Kubernetes..."
kubectl apply -f k8s/slice-client-service.yaml -n lb-demo

# 5. Restart deployments to ensure new images are used
echo -e "\nRestarting deployments to use new images..."
kubectl rollout restart deployment/slice-client-service-zone-a -n lb-demo
kubectl rollout restart deployment/slice-client-service-zone-b -n lb-demo

# 6. Wait for deployments to be ready
echo -e "\nWaiting for deployments to be ready..."
kubectl wait --for=condition=Available deployment/slice-client-service-zone-a -n lb-demo --timeout=300s
kubectl wait --for=condition=Available deployment/slice-client-service-zone-b -n lb-demo --timeout=300s

echo -e "\n================================"
echo "Deployment Complete!"
echo "================================"

echo -e "\nPods status:"
kubectl get pods -n lb-demo -l app=slice-client-service -o wide

echo -e "\nNext steps:"
echo "  Check logs: kubectl logs -n lb-demo -l app=slice-client-service --tail=100"
echo "  Test: kubectl exec -n lb-demo <pod-name> -- curl -s http://localhost:8081/test-loadbalancing?calls=10"

