#!/bin/bash

ZONE="${1:-zone-a}"

echo "================================"
echo "Setting up debug for simple-client-service-$ZONE"
echo "================================"
echo ""

# Get the pod name
POD=$(kubectl get pod -n lb-demo -l app=simple-client-service,zone=$ZONE -o jsonpath='{.items[0].metadata.name}')

if [ -z "$POD" ]; then
    echo "❌ No pod found for zone $ZONE"
    exit 1
fi

echo "Found pod: $POD"
echo ""
echo "Setting up port forwarding..."
echo "  - HTTP: localhost:8081 -> pod:8081"
echo "  - Debug: localhost:5005 -> pod:5005"
echo ""
echo "Configure your IDE to connect to:"
echo "  Host: localhost"
echo "  Port: 5005"
echo "  Debugger: Remote JVM Debug"
echo ""
echo "Press Ctrl+C to stop port forwarding"
echo ""

kubectl port-forward -n lb-demo $POD 8081:8081 5005:5005

