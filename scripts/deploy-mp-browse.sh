#!/bin/bash

set -e

NAMESPACE="lb-demo"

# Load image into Kind cluster
echo ""
echo "Loading image into Kind cluster..."
kind load docker-image 268936375139.dkr.ecr.eu-central-1.amazonaws.com/treatwell/mp-browse:local-SNAPSHOT --name lb-demo

# Deploy to Kubernetes
echo ""
echo "Deploying to Kubernetes..."
kubectl apply -f k8s/mp-browse.yaml

# Force restart to pick up new images
echo ""
echo "Restarting deployments to use new images..."
kubectl rollout restart deployment/mp-browse-zone-a -n ${NAMESPACE}
kubectl rollout restart deployment/mp-browse-zone-b -n ${NAMESPACE}

# Wait for deployments
echo ""
echo "Waiting for deployments to be ready..."
kubectl wait --for=condition=available --timeout=120s \
  deployment/mp-browse-zone-a \
  deployment/mp-browse-zone-b \
  -n ${NAMESPACE}

echo ""
echo "========================================"
echo "MP-Browse Deployed Successfully!"
echo "========================================"
echo ""
echo "Check deployment status:"
echo "  kubectl get pods -n ${NAMESPACE} -l app=mp-browse"
echo ""
echo "View logs:"
echo "  kubectl logs -n ${NAMESPACE} -l app=mp-browse,zone=zone-a --tail=50"
echo "  kubectl logs -n ${NAMESPACE} -l app=mp-browse,zone=zone-b --tail=50"
echo ""
echo "Access mp-browse service:"
echo "  kubectl port-forward -n ${NAMESPACE} svc/mp-browse 3335:3335"
echo "  Then visit: http://localhost:3335"
echo ""
echo "For debugging (remote debug port 5005):"
echo "  kubectl port-forward -n ${NAMESPACE} svc/mp-browse 5005:5005"
echo ""
echo "Get service URL (NodePort):"
echo "  kubectl get svc mp-browse -n ${NAMESPACE}"
echo ""

