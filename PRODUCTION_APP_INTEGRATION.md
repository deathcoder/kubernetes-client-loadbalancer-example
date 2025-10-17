# Production Application Integration Guide

This guide explains how to integrate your production application (mp-browse / browse-webapp) into the local Kind cluster for testing zone-aware load balancing.

## Overview

Instead of just testing with demo applications, you can deploy your actual production application into the local Kind cluster. This allows you to:

- ✅ Validate zone-aware routing with your real application and configuration
- ✅ Test locally before deploying to staging/production
- ✅ Debug issues with EndpointSlice-based zone filtering
- ✅ Verify that your load balancer configuration works correctly
- ✅ Compare behavior between test clients and your actual app

## Prerequisites

1. Your browse-webapp JAR file (e.g., `browse-webapp-local-SNAPSHOT.jar`)
2. Kind cluster already set up (`./scripts/setup-kind-cluster.sh`)
3. Sample service deployed (included in all deployment scripts)

## Quick Setup

### Step 1: Copy Your JAR

```bash
# Copy your application JAR to the mp-browse directory
cp /path/to/browse-webapp/target/browse-webapp-local-SNAPSHOT.jar mp-browse/app.jar
```

### Step 2: Deploy

```bash
./scripts/build-and-deploy-mp-browse.sh
```

This will:
1. Verify `app.jar` exists
2. Build Docker image `mp-browse:latest`
3. Load image into Kind cluster
4. Deploy two instances (zone-a and zone-b)
5. Wait for pods to be ready

### Step 3: Verify

```bash
# Check pods
kubectl get pods -n lb-demo -l app=mp-browse

# Check logs
kubectl logs -n lb-demo -l app=mp-browse,zone=zone-a --tail=50 -f
```

## Architecture

```
┌───────────────────────────────────────────────────────────────┐
│                     Kind Cluster (lb-demo)                     │
│                                                                │
│  ┌─────────────────────┐         ┌─────────────────────┐     │
│  │     Zone A          │         │     Zone B          │     │
│  │                     │         │                     │     │
│  │  mp-browse-zone-a   │         │  mp-browse-zone-b   │     │
│  │  (Your Prod App)    │         │  (Your Prod App)    │     │
│  │                     │         │                     │     │
│  │         ↓           │         │         ↓           │     │
│  │    Prefers          │         │    Prefers          │     │
│  │         ↓           │         │         ↓           │     │
│  │  sample-service-a1  │         │  sample-service-b1  │     │
│  │  sample-service-a2  │         │  sample-service-b2  │     │
│  │  (zone-a)           │         │  (zone-b)           │     │
│  └─────────────────────┘         └─────────────────────┘     │
│                                                                │
│  Zone-aware routing via EndpointSlices                        │
└───────────────────────────────────────────────────────────────┘
```

## What Gets Deployed

### Deployments

Two deployments are created:

```yaml
mp-browse-zone-a:
  replicas: 1
  zone: zone-a
  labels:
    - app: mp-browse
    - zone: zone-a
    - topology.kubernetes.io/zone: zone-a
  ports:
    - 3335 (HTTP)
    - 5005 (debug)

mp-browse-zone-b:
  replicas: 1
  zone: zone-b
  labels:
    - app: mp-browse
    - zone: zone-b
    - topology.kubernetes.io/zone: zone-b
  ports:
    - 3335 (HTTP)
    - 5005 (debug)
```

### Environment Variables

Your application will have these environment variables:

```bash
# Zone identification
ZONE=zone-a  # or zone-b
POD_NAME=<actual-pod-name>

# AWS Configuration (from your original setup)
AWS_REGION=eu-west-1
AWS_DEFAULT_REGION=eu-west-1

# Spring Cloud Configuration
SPRING_CLOUD_LOADBALANCER_ENABLED=true
SPRING_CLOUD_LOADBALANCER_ZONE=zone-a  # or zone-b
SPRING_CLOUD_KUBERNETES_DISCOVERY_ENABLED=true
SPRING_CLOUD_KUBERNETES_CLIENT_NAMESPACE=lb-demo
KUBERNETES_NAMESPACE=lb-demo

# Application-specific
SERVICE_MARKETPLACE_BROWSE_INDEX_INDEXINGENABLED=false
```

### Service

```yaml
Service: mp-browse
Type: NodePort
Selector: app=mp-browse
Ports:
  - HTTP: 3335 → NodePort 30080
  - Debug: 5005 → NodePort 30085
```

## Testing Zone-Aware Routing

### Verify Deployment

```bash
# Check pod status
kubectl get pods -n lb-demo -l app=mp-browse

# Expected output:
# NAME                                READY   STATUS    RESTARTS   AGE
# mp-browse-zone-a-xxxxxxxxxx-xxxxx   1/1     Running   0          1m
# mp-browse-zone-b-xxxxxxxxxx-xxxxx   1/1     Running   0          1m
```

### Check Logs for Zone Filtering

```bash
# Zone-A logs
kubectl logs -n lb-demo -l app=mp-browse,zone=zone-a --tail=100 -f
```

Look for log entries like:

```
FILTERING INSTANCES USING ENDPOINTSLICE ZONE INFORMATION
Client Zone: zone-a
Total Instances: 4
--------------------------------------------------------------------------------
Instance: sample-service-zone-a-xxx (10.244.1.5:8080) - Zone: zone-a - Same Zone: true
Instance: sample-service-zone-a-yyy (10.244.1.6:8080) - Zone: zone-a - Same Zone: true
Instance: sample-service-zone-b-xxx (10.244.2.5:8080) - Zone: zone-b - Same Zone: false
Instance: sample-service-zone-b-yyy (10.244.2.6:8080) - Zone: zone-b - Same Zone: false
Found 2 instances in the same zone (zone-a) as the client.
```

### Access Your Application

#### Option 1: Port Forward to Service

```bash
# Forward to the mp-browse service (round-robins to both zones)
kubectl port-forward -n lb-demo svc/mp-browse 3335:3335

# Access at: http://localhost:3335
```

#### Option 2: Port Forward to Specific Zone

```bash
# Forward to zone-a pod only
kubectl port-forward -n lb-demo deployment/mp-browse-zone-a 3335:3335

# Or to zone-b pod
kubectl port-forward -n lb-demo deployment/mp-browse-zone-b 3335:3335
```

### Run Comprehensive Tests

```bash
./scripts/test-loadbalancing.sh
```

This will test all deployed clients including mp-browse and show:
- Pod status
- Recent logs
- Instructions for accessing your app
- Debugging tips

## Expected Behavior

If zone-aware load balancing is working correctly:

1. **mp-browse-zone-a** should:
   - Discover all 4 sample-service instances via Kubernetes API
   - Query EndpointSlices for zone information
   - Filter to only zone-a instances (2 pods)
   - Route 100% of traffic to zone-a sample-service pods

2. **mp-browse-zone-b** should:
   - Discover all 4 sample-service instances
   - Filter to only zone-b instances (2 pods)
   - Route 100% of traffic to zone-b sample-service pods

## Debugging

### Check EndpointSlice Discovery

```bash
# View EndpointSlices
kubectl get endpointslices -n lb-demo

# Describe a specific EndpointSlice
kubectl describe endpointslice -n lb-demo -l kubernetes.io/service-name=sample-service
```

Expected output should show zone information:

```yaml
Endpoints:
  - Addresses:
      - 10.244.1.5
    Conditions:
      Ready: true
    Zone: zone-a
```

### Check RBAC Permissions

```bash
# Verify service account
kubectl get sa spring-cloud-kubernetes -n lb-demo

# Verify role binding
kubectl get rolebinding -n lb-demo

# Check role permissions
kubectl describe role spring-cloud-kubernetes -n lb-demo
```

The role should include:

```yaml
Resources:
  - endpointslices.discovery.k8s.io
Verbs:
  - get
  - list
  - watch
```

### Remote Debugging

Enable remote debugging on port 5005:

```bash
# Forward debug port for zone-a
kubectl port-forward -n lb-demo deployment/mp-browse-zone-a 5005:5005
```

Then connect your IDE debugger to `localhost:5005`.

**Suggested Breakpoints:**
- `EndpointSliceZoneServiceInstanceListSupplier.get()` - See instance filtering
- `EndpointSliceZoneServiceInstanceListSupplier.refreshZoneCache()` - See EndpointSlice queries

### Common Issues

#### 1. JAR Not Found

```
❌ ERROR: mp-browse/app.jar not found!
```

**Solution**: Copy your JAR to `mp-browse/app.jar` before deploying.

#### 2. Pods Not Starting

```bash
kubectl describe pod -n lb-demo -l app=mp-browse
```

Check for:
- Image pull errors
- Insufficient resources
- Application startup failures

#### 3. Zone Information Missing

If logs show "No zone found in EndpointSlice cache":

1. Check EndpointSlices have zone info:
   ```bash
   kubectl get endpointslice -n lb-demo -o yaml | grep -A 5 zone
   ```

2. Verify pod labels:
   ```bash
   kubectl get pods -n lb-demo -L topology.kubernetes.io/zone
   ```

3. Check RBAC permissions for endpointslices resource

#### 4. Still Getting 50% Same-Zone Routing

This usually means EndpointSlice zone filtering isn't working. Check:

1. Your application's load balancer configuration
2. That you're using the EndpointSlice-based supplier
3. Logs for "FILTERING INSTANCES USING ENDPOINTSLICE" messages

## Updating Your Application

To deploy a new version of your JAR:

```bash
# 1. Copy new JAR
cp /path/to/new/browse-webapp.jar mp-browse/app.jar

# 2. Rebuild and redeploy
./scripts/build-and-deploy-mp-browse.sh
```

The script will:
- Build a new Docker image
- Load it into Kind
- Perform a rolling restart
- Wait for new pods to be ready

## Cleanup

### Remove Only MP-Browse

```bash
kubectl delete -f k8s/mp-browse.yaml
```

### Remove Everything

```bash
./scripts/cleanup.sh
```

Or for complete cleanup including Docker images:

```bash
./scripts/cleanup-all.sh
```

## Comparing with Other Implementations

You can compare your production app with the three test implementations:

| Implementation | Approach | Code Location |
|----------------|----------|---------------|
| **MP-Browse** | Your production app (EndpointSlices) | mp-browse/ |
| Custom Client | K8s API pod label queries | client-service/ |
| Simple Client | podMetadata() access | simple-client-service/ |
| Slice Client | EndpointSlices (demo version) | slice-client-service/ |

Deploy all and compare:

```bash
./scripts/build-and-deploy.sh
./scripts/build-and-deploy-simple.sh
./scripts/build-and-deploy-slice.sh
./scripts/build-and-deploy-mp-browse.sh

./scripts/test-loadbalancing.sh
```

## Next Steps

Once you've verified zone-aware routing works locally:

1. ✅ Commit your load balancer configuration changes
2. ✅ Deploy to staging with zone labels configured
3. ✅ Monitor same-zone vs cross-zone metrics
4. ✅ Gradually roll out to production

See `FINDINGS_SUMMARY.md` for detailed information about the zone-aware routing implementation and the issues with Spring Cloud's built-in zone preference.

