# Zone-Aware Load Balancing with Spring Cloud Kubernetes

## Executive Summary

This project implements **zone-aware load balancing** for Spring Cloud applications running in Kubernetes **without requiring a service mesh**. The solution ensures that client services only route traffic to backend services within the same availability zone, reducing cross-zone traffic costs and latency.

**Result:** 100% same-zone traffic routing with proper fallback to cross-zone when no same-zone instances are available.

---

## Problem Statement

### Business Requirements
- Minimize cross-zone network traffic to reduce costs and latency
- Maintain high availability with automatic fallback to other zones if needed
- Work with `RestTemplate` (blocking HTTP client)
- No service mesh available in the environment

### Development Challenge
The original development loop was slow because testing required:
1. Building the application
2. Creating Docker images
3. Deploying to a Kubernetes cluster
4. Testing the behavior
5. Checking logs in the cluster

This made iterating on the load balancer logic very time-consuming.

---

## What We Expected from Spring Cloud Kubernetes (But Didn't Work)

### Initial Expectations

Based on Spring Cloud Kubernetes documentation, we expected:

1. **Built-in Zone Preference**: The `ZonePreferenceServiceInstanceListSupplier` should automatically filter service instances by zone
2. **Automatic Metadata Propagation**: Pod labels (including zone labels) would automatically be available in `ServiceInstance` metadata
3. **Simple Configuration**: Just setting `spring.cloud.kubernetes.loadbalancer.mode=POD` and `spring.cloud.kubernetes.loadbalancer.zone-preference-enabled=true` would work

### What Actually Happened

**The zone preference didn't work** because:

1. **Missing Zone Metadata**: When using `POD` mode, Spring Cloud Kubernetes Discovery doesn't automatically include pod labels in the `ServiceInstance` metadata in a way that `ZonePreferenceServiceInstanceListSupplier` can use

2. **Metadata Structure Mismatch**: The available metadata was:
   ```
   [app, port.http, k8s_namespace, type, kubectl.kubernetes.io/last-applied-configuration]
   ```
   But the zone information from pod labels (`topology.kubernetes.io/zone` or `zone`) was not present

3. **POD Mode Limitation**: In POD mode, Spring Cloud Kubernetes provides:
   - **Host**: Pod IP address (e.g., `10.244.1.20`)
   - **Instance ID**: UUID (e.g., `57ed3e4c-a932-44e6-aa62-78cefda8729d`)
   
   Neither of these can be directly used to query the Kubernetes API for pod details without additional logic

4. **Load Balancer Context Isolation**: The `ServiceInstanceListSupplier` runs in a separate Spring child context (per service), making it difficult to access application beans like `KubernetesClient`

---

## The Solution

### Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│  Client Application (zone-a)                                 │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  RestTemplate (@LoadBalanced)                        │   │
│  └─────────────────┬────────────────────────────────────┘   │
│                    │                                         │
│  ┌─────────────────▼────────────────────────────────────┐   │
│  │  CustomZonePreferenceServiceInstanceListSupplier     │   │
│  │  - Reads client zone from configuration              │   │
│  │  - Fetches all service instances                     │   │
│  │  - Queries Kubernetes API by pod IP                  │   │
│  │  - Extracts zone labels from pod metadata            │   │
│  │  - Filters instances to same zone                    │   │
│  └─────────────────┬────────────────────────────────────┘   │
│                    │                                         │
└────────────────────┼─────────────────────────────────────────┘
                     │
        ┌────────────▼─────────────┐
        │  Kubernetes API Server   │
        │  - Pod metadata lookup   │
        │  - Zone label extraction │
        └────────────┬─────────────┘
                     │
        ┌────────────▼─────────────┐
        │  Service Instances       │
        │  - zone-a pods only      │
        │  (100% same-zone)        │
        └──────────────────────────┘
```

### Key Components

#### 1. Custom ServiceInstanceListSupplier

**File:** `CustomZonePreferenceServiceInstanceListSupplier.java`

This is the heart of the solution. It:

- **Wraps** the default discovery client supplier
- **Reads** the client's zone from configuration (`spring.cloud.loadbalancer.zone`)
- **Queries** all service instances for a given service
- **Enriches** each instance with zone information by querying the Kubernetes API
- **Filters** instances to only those in the same zone
- **Falls back** to all instances if no same-zone instances are available

**Critical Implementation Detail - IP-based Pod Lookup:**

```java
// Get pod IP from ServiceInstance
String podIp = instance.getHost();  // e.g., "10.244.1.20"

// Query all pods and find by IP
List<Pod> pods = kubernetesClient.pods()
    .inNamespace(namespace)
    .list()
    .getItems();

Pod matchingPod = pods.stream()
    .filter(pod -> podIp.equals(pod.getStatus().getPodIP()))
    .findFirst()
    .orElse(null);

// Extract zone from pod labels
String zone = matchingPod.getMetadata().getLabels().get("zone");
// or
String zone = matchingPod.getMetadata().getLabels().get("topology.kubernetes.io/zone");
```

**Why IP-based lookup?** In POD mode, the `ServiceInstance.getHost()` returns the pod's IP address, not a DNS name. We cannot extract a pod name from an IP, so we must query all pods and match by IP.

#### 2. Load Balancer Configuration

**File:** `LoadBalancerConfig.java`

```java
@Configuration
@LoadBalancerClient(
    name = "sample-service", 
    configuration = LoadBalancerConfig.SampleServiceLoadBalancerConfig.class
)
public class LoadBalancerConfig {
    
    public static class SampleServiceLoadBalancerConfig {
        
        @Bean
        public ServiceInstanceListSupplier serviceInstanceListSupplier(
                ConfigurableApplicationContext context) {
            
            // Build base supplier
            ServiceInstanceListSupplier delegate = 
                ServiceInstanceListSupplier.builder()
                    .withDiscoveryClient()
                    .build(context);
            
            // Get KubernetesClient from parent context
            KubernetesClient kubernetesClient = null;
            try {
                kubernetesClient = context.getBean(KubernetesClient.class);
            } catch (Exception e) {
                // Try parent context
                if (context.getParent() != null) {
                    kubernetesClient = context.getParent()
                        .getBean(KubernetesClient.class);
                }
            }
            
            // Wrap with custom zone-aware supplier
            return new CustomZonePreferenceServiceInstanceListSupplier(
                delegate, zone, kubernetesClient
            );
        }
    }
}
```

**Key Points:**
- Uses `@LoadBalancerClient` to apply custom configuration per service
- Retrieves `KubernetesClient` from parent context (load balancer child contexts don't have direct access)
- Chains the custom supplier with the default discovery supplier

#### 3. Kubernetes Client Bean

**File:** `KubernetesClientConfig.java`

```java
@Configuration
public class KubernetesClientConfig {
    
    @Bean
    public KubernetesClient kubernetesClient() {
        return new KubernetesClientBuilder().build();
    }
}
```

This ensures a `KubernetesClient` bean is available in the application context for querying pod metadata.

#### 4. Application Configuration

**File:** `application.yml`

```yaml
spring:
  cloud:
    kubernetes:
      enabled: true
      discovery:
        enabled: true
        metadata:
          add-pod-labels: true
          add-pod-annotations: true
      loadbalancer:
        enabled: true
        mode: POD  # Use POD mode for direct pod routing
    loadbalancer:
      zone: ${ZONE:unknown}  # Injected via deployment environment
```

**Key Configuration:**
- `mode: POD` - Enables direct pod-to-pod communication
- `zone: ${ZONE}` - Injected via environment variable in Kubernetes deployment
- `add-pod-labels: true` - Attempts to include pod labels (though not sufficient alone)

#### 5. Kubernetes Deployment Configuration

**File:** `k8s/client-service.yaml`

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: client-service-zone-a
spec:
  template:
    metadata:
      labels:
        zone: zone-a  # Pod label for zone identification
    spec:
      containers:
      - name: client-service
        env:
        - name: ZONE
          value: "zone-a"  # Injected into application
```

**Dual Zone Configuration:**
- **Pod Label** (`zone: zone-a`): Used by Kubernetes API queries
- **Environment Variable** (`ZONE=zone-a`): Used by application to know its own zone

---

## Why This Solution Is Needed

### 1. Cost Optimization
Cross-zone data transfer in cloud environments (AWS, GCP, Azure) incurs charges. By keeping traffic within zones, you can reduce these costs significantly.

### 2. Latency Reduction
Same-zone communication has lower latency than cross-zone, improving application performance.

### 3. No Service Mesh Required
Service meshes like Istio or Linkerd provide zone-aware routing out of the box, but they add:
- Operational complexity
- Resource overhead (sidecar proxies)
- Learning curve for the team

This solution achieves the same goal without those trade-offs.

### 4. Works with RestTemplate
The solution integrates seamlessly with Spring's `@LoadBalanced RestTemplate`, requiring no changes to existing service-to-service communication code.

### 5. Graceful Fallback
If all same-zone instances are down, traffic automatically fails over to other zones, maintaining high availability.

---

## Local Development Environment

### The Challenge
Testing zone-aware behavior requires a multi-zone Kubernetes environment, which is difficult to replicate locally.

### The Solution: Kind with Zone Labels

We created a local Kind cluster with:
- **3 worker nodes** labeled with different zones
- **Pod scheduling** to specific nodes based on zone labels
- **Automated scripts** for cluster setup, build, and deployment

**File:** `scripts/setup-kind-cluster.sh`

```bash
# Create cluster with multiple worker nodes
kind create cluster --config=- <<EOF
kind: Cluster
apiVersion: kind.x-k8s.io/v1alpha4
nodes:
- role: control-plane
- role: worker
- role: worker  
- role: worker
EOF

# Label nodes as different zones
kubectl label node lb-demo-worker topology.kubernetes.io/zone=zone-a
kubectl label node lb-demo-worker2 topology.kubernetes.io/zone=zone-a
kubectl label node lb-demo-worker3 topology.kubernetes.io/zone=zone-b
```

### Fast Development Loop

1. **Make code changes**
2. **Run:** `./scripts/build-and-deploy.sh`
   - Builds Maven artifacts
   - Creates Docker images
   - Loads images into Kind
   - Deploys to Kubernetes
   - Restarts deployments
3. **Test:** `./scripts/test-loadbalancing.sh`
   - Shows pod distribution
   - Tests from zone-a client
   - Tests from zone-b client
   - Displays statistics

**Iteration time:** ~30 seconds vs. several minutes in a real cluster.

---

## Testing and Validation

### Test Results

```
================================
Testing from zone-a client
================================
Client pod: client-service-zone-a-5fdc86c8c-r9xbx

totalCalls: 20
sameZoneCalls: 20
crossZoneCalls: 0
sameZonePercentage: "100.0%"

podDistribution: {
  "sample-service-zone-a-6f74896b6d-nh5rb": 10,
  "sample-service-zone-a-6f74896b6d-nwsqt": 10
}

================================
Testing from zone-b client
================================
Client pod: client-service-zone-b-6cc4cd7ddc-fj825

totalCalls: 20
sameZoneCalls: 20
crossZoneCalls: 0
sameZonePercentage: "100.0%"

podDistribution: {
  "sample-service-zone-b-757bccf8cb-qcjzb": 10,
  "sample-service-zone-b-757bccf8cb-g8hwn": 10
}
```

### Validation Points

✅ **Zone Isolation**: 100% of traffic stays within the same zone  
✅ **Load Distribution**: Evenly distributes across pods within the zone  
✅ **RestTemplate Integration**: Works seamlessly with `@LoadBalanced` annotation  
✅ **Fallback Capability**: Falls back to cross-zone if no same-zone instances available  
✅ **Fast Development**: Local testing completes in seconds

---

## Lessons Learned

### 1. Spring Cloud Kubernetes Metadata Limitations

**Problem:** Pod labels are not automatically exposed in a usable format in `ServiceInstance` metadata when using POD mode.

**Learning:** You need to explicitly query the Kubernetes API to get pod labels for zone information.

### 2. Load Balancer Context Isolation

**Problem:** `ServiceInstanceListSupplier` runs in a child Spring context that doesn't have access to application beans by default.

**Learning:** Access the parent context explicitly: `context.getParent().getBean(KubernetesClient.class)`

### 3. POD Mode Uses IP Addresses

**Problem:** In POD mode, `ServiceInstance.getHost()` returns IP addresses, not DNS names.

**Learning:** Query pods by IP address instead of trying to extract pod names: 
```java
pods.stream()
    .filter(pod -> podIp.equals(pod.getStatus().getPodIP()))
    .findFirst()
```

### 4. RBAC Requirements

**Problem:** The application needs permissions to query pod metadata.

**Learning:** Create appropriate `ServiceAccount`, `ClusterRole`, and `ClusterRoleBinding`:
```yaml
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRole
metadata:
  name: spring-cloud-kubernetes-role
rules:
- apiGroups: [""]
  resources: ["pods", "services", "endpoints"]
  verbs: ["get", "list", "watch"]
```

### 5. Maven Compiler Parameters

**Problem:** Spring Cloud LoadBalancer reflection failed with `IllegalArgumentException: Name for argument of type [int] not specified`.

**Learning:** Enable parameter name preservation:
```xml
<properties>
    <maven.compiler.parameters>true</maven.compiler.parameters>
</properties>
```

---

## Production Considerations

### Performance

**Concern:** Querying the Kubernetes API for every load balancing decision could be slow.

**Mitigation:**
- Spring Cloud LoadBalancer caches `ServiceInstance` lists
- Caching can be configured with `spring.cloud.loadbalancer.cache.ttl`
- Consider implementing an in-memory cache for pod IP → zone mappings

### Scalability

**Concern:** Listing all pods in a namespace for IP lookup might not scale.

**Mitigation:**
- Use field selectors if possible
- Implement pod IP → zone caching
- Consider using Kubernetes Informers for watch-based updates

### High Availability

**Concern:** What if all same-zone instances are down?

**Solution:** The implementation automatically falls back to all instances:
```java
if (sameZoneInstances.isEmpty()) {
    log.warn("No instances found in zone {}, falling back to all instances", clientZone);
    return instances;  // Cross-zone fallback
}
```

### Monitoring

**Recommendations:**
- Add metrics for same-zone vs. cross-zone traffic percentages
- Alert when fallback to cross-zone occurs frequently
- Monitor Kubernetes API query performance

---

## Alternative Approaches Considered

### 1. Service Mesh (Istio/Linkerd)
**Pros:** Built-in zone-aware routing, no custom code  
**Cons:** Operational complexity, resource overhead  
**Decision:** Not available in the environment

### 2. Spring Cloud LoadBalancer's Built-in ZonePreferenceServiceInstanceListSupplier
**Pros:** No custom code needed  
**Cons:** Doesn't work with Spring Cloud Kubernetes POD mode without metadata enrichment  
**Decision:** Needed custom implementation

### 3. DNS-based Service Discovery (SERVICE mode)
**Pros:** Simpler, uses Kubernetes DNS  
**Cons:** Doesn't provide pod-level zone information, routing happens at kube-proxy level  
**Decision:** POD mode gives more control

### 4. Custom Kubernetes Controller
**Pros:** Could inject zone annotations into endpoints  
**Cons:** Requires cluster-wide permissions, operational overhead  
**Decision:** Application-level solution is simpler

---

## Conclusion

This solution demonstrates how to implement **production-grade zone-aware load balancing** in Spring Cloud Kubernetes without a service mesh. The key insights are:

1. **Spring Cloud Kubernetes POD mode** doesn't automatically expose pod labels in usable metadata
2. **Direct Kubernetes API queries** are necessary to enrich service instances with zone information
3. **IP-based pod lookup** is required because POD mode uses IP addresses, not DNS names
4. **Custom ServiceInstanceListSupplier** provides the flexibility to implement zone-aware filtering
5. **Local Kind clusters with zone labels** enable fast development and testing

The result is a **cost-effective, low-latency, highly available** service-to-service communication pattern that works with existing `RestTemplate` code and provides 100% same-zone traffic routing with automatic cross-zone fallback.

---

## References

- [Spring Cloud Kubernetes Load Balancer Documentation](https://docs.spring.io/spring-cloud-kubernetes/reference/load-balancer.html)
- [Spring Cloud LoadBalancer Documentation](https://docs.spring.io/spring-cloud-commons/docs/current/reference/html/#spring-cloud-loadbalancer)
- [Kubernetes Topology Labels](https://kubernetes.io/docs/reference/labels-annotations-taints/#topologykubernetesiozone)
- [Kind - Kubernetes in Docker](https://kind.sigs.k8s.io/)
- [Fabric8 Kubernetes Client](https://github.com/fabric8io/kubernetes-client)

