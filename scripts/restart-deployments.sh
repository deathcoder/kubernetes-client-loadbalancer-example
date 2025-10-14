#!/bin/bash

# Force restart all deployments to pick up new images
# Useful when images have been updated but deployments show "unchanged"

set -e

NAMESPACE="lb-demo"

echo "================================"
echo "Restarting All Deployments"
echo "================================"
echo ""

# Restart all deployments
kubectl rollout restart deployment/sample-service-zone-a -n ${NAMESPACE}
kubectl rollout restart deployment/sample-service-zone-b -n ${NAMESPACE}
kubectl rollout restart deployment/client-service-zone-a -n ${NAMESPACE}
kubectl rollout restart deployment/client-service-zone-b -n ${NAMESPACE}

echo ""
echo "Waiting for rollouts to complete..."
kubectl rollout status deployment/sample-service-zone-a -n ${NAMESPACE}
kubectl rollout status deployment/sample-service-zone-b -n ${NAMESPACE}
kubectl rollout status deployment/client-service-zone-a -n ${NAMESPACE}
kubectl rollout status deployment/client-service-zone-b -n ${NAMESPACE}

echo ""
echo "================================"
echo "Restart Complete!"
echo "================================"
echo ""
kubectl get pods -n ${NAMESPACE} -L zone -o wide

