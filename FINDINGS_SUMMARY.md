# Zone-Aware Load Balancing with Spring Cloud Kubernetes - Findings Summary

## TL;DR

**Spring Cloud's built-in zone-aware load balancing doesn't work with Spring Cloud Kubernetes Discovery** because zone information is stored in `podMetadata()` but the built-in `ZonePreferenceServiceInstanceListSupplier` only checks `getMetadata()`.

✅ **Solution**: Custom `ServiceInstanceListSupplier` that accesses `podMetadata()` or uses Kubernetes EndpointSlices API.

---

## The Problem

### What We Expected
```yaml
spring:
  cloud:
    kubernetes:
      loadbalancer:
        mode: POD
        zone-preference-enabled: true
    loadbalancer:
      zone: zone-a
      configurations: zone-preference
```

With this configuration, we expected 100% same-zone routing.

### What We Got
- **50% same-zone routing** (random distribution)
- Zone filtering was not working at all

---

## Investigation Results

### 1. Zone Information IS Available

When we inspected `DefaultKubernetesServiceInstance`:

```java
// ✅ THIS WORKS - Zone is in podMetadata()
Map<String, Map<String, String>> podMetadata = instance.podMetadata();
String zone = podMetadata.get("labels").get("topology.kubernetes.io/zone");
// Returns: "zone-a"

// ❌ THIS DOESN'T WORK - Zone is NOT in getMetadata()
Map<String, String> metadata = instance.getMetadata();
String zone = metadata.get("zone");
// Returns: null
```

### 2. Why Built-in Zone Preference Fails

`ZonePreferenceServiceInstanceListSupplier` uses:
```java
private String getZone(ServiceInstance serviceInstance) {
    Map<String, String> metadata = serviceInstance.getMetadata();
    if (metadata != null) {
        return metadata.get(ZONE); // ❌ Never finds the zone!
    }
    return null;
}
```

It **only** checks `getMetadata()`, which doesn't contain zone information from pod labels.

---

## Working Solutions

We implemented and tested three different approaches:

### Approach 1: Direct Pod Label Query ✅ 100%

**How it works**: Queries Kubernetes API for pod details by IP, gets zone from pod labels

**Pros:**
- Simple and direct
- No custom labels needed (uses `topology.kubernetes.io/zone`)

**Cons:**
- Extra API calls per instance discovery
- Requires additional RBAC permissions for pods

**Code**: `client-service/CustomZonePreferenceServiceInstanceListSupplier.java`

### Approach 2: PodMetadata Access ✅ 100%

**How it works**: Accesses `DefaultKubernetesServiceInstance.podMetadata()` directly

**Pros:**
- No additional API calls
- Uses existing discovery data
- Cleanest approach

**Cons:**
- Requires casting to `DefaultKubernetesServiceInstance`
- Kubernetes-specific code

**Code**: `simple-client-service/LoggingServiceInstanceListSupplier.java`

### Approach 3: EndpointSlices API ✅ 100%

**How it works**: Uses Kubernetes EndpointSlices which have native zone support

**Pros:**
- Kubernetes-native approach
- EndpointSlices designed for topology-aware routing
- Future-proof (EndpointSlices are the successor to Endpoints)

**Cons:**
- Requires additional RBAC for `endpointslices` resource
- More complex implementation
- Extra API calls

**Code**: `slice-client-service/EndpointSliceZoneServiceInstanceListSupplier.java`

---

## Test Results

| Implementation | Approach | Zone-A→Zone-A | Zone-B→Zone-B | Status |
|---------------|----------|---------------|---------------|---------|
| Built-in (`withZonePreference()`) | Uses `getMetadata()` | 50% | 50% | ❌ Broken |
| Custom Client | Pod API queries | 100% | 100% | ✅ Working |
| Simple Client | `podMetadata()` access | 100% | 100% | ✅ Working |
| Slice Client | EndpointSlices API | 100% | 100% | ✅ Working |

---

## Architectural Issue

The problem is an **architectural mismatch** between two Spring Cloud projects:

### Spring Cloud LoadBalancer (Commons)
- Expects zone information in `ServiceInstance.getMetadata()`
- Uses a generic interface that works across different service discovery systems

### Spring Cloud Kubernetes
- Stores pod-specific data (including labels) in `DefaultKubernetesServiceInstance.podMetadata()`
- Keeps pod metadata separate from service metadata
- Doesn't automatically flatten pod labels into `getMetadata()`

---

## Recommendations

### For Production Use

**Option 1: PodMetadata Access (Recommended)**
- Cleanest implementation
- No extra API calls
- Uses existing discovery data

**Option 2: EndpointSlices**
- If you want to follow Kubernetes best practices
- Good for future-proofing
- Requires additional RBAC setup

**Option 3: Direct Pod Query**
- If you need maximum flexibility
- Good if you need other pod information too

### For Spring Cloud Team

**Consider one of these fixes:**

1. **Auto-flatten pod labels** into `getMetadata()` when `add-pod-labels: true`
   ```yaml
   spring.cloud.kubernetes.discovery.metadata:
     add-pod-labels: true
     # Should also add labels to getMetadata(), not just podMetadata()
   ```

2. **Create Kubernetes-aware zone supplier**
   ```java
   public class KubernetesZonePreferenceServiceInstanceListSupplier 
       extends ZonePreferenceServiceInstanceListSupplier {
       
       @Override
       protected String getZone(ServiceInstance instance) {
           // Check podMetadata() for Kubernetes instances
           if (instance instanceof DefaultKubernetesServiceInstance) {
               return getZoneFromPodMetadata((DefaultKubernetesServiceInstance) instance);
           }
           return super.getZone(instance);
       }
   }
   ```

3. **Configuration to map labels to metadata**
   ```yaml
   spring.cloud.kubernetes.discovery.metadata:
     flatten-labels:
       - topology.kubernetes.io/zone: zone
   ```

---

## Files Reference

### Issue Documents
- `SPRING_CLOUD_ISSUE.md` - Complete GitHub issue text
- `ISSUE_SUBMISSION_GUIDE.md` - How and where to submit

### Working Implementations
- `client-service/` - Pod label query approach
- `simple-client-service/` - PodMetadata access approach  
- `slice-client-service/` - EndpointSlices API approach

### Test & Deploy
- `scripts/test-loadbalancing.sh` - Compare all implementations
- `scripts/build-and-deploy.sh` - Deploy custom client
- `scripts/build-and-deploy-simple.sh` - Deploy simple client
- `scripts/build-and-deploy-slice.sh` - Deploy slice client

### Documentation
- `SOLUTION.md` - Complete solution documentation
- `DEBUG_GUIDE.md` - Remote debugging setup
- `README.md` - Project overview

---

## Key Takeaways

1. **Zone information exists** in Spring Cloud Kubernetes, but in the wrong place for Spring Cloud LoadBalancer to find it

2. **Built-in zone preference is broken** for Kubernetes discovery and requires custom implementation

3. **Three working approaches** exist, each with different trade-offs

4. **This is likely a bug or gap** in the integration between Spring Cloud LoadBalancer and Spring Cloud Kubernetes

5. **Custom suppliers work perfectly** and can achieve 100% zone-aware routing

---

## Next Steps

1. ✅ Submit issue to Spring Cloud Kubernetes (use `SPRING_CLOUD_ISSUE.md`)
2. ⏳ Wait for maintainer response
3. ⏳ Test any suggested configurations
4. ⏳ Consider contributing a PR if requested
5. ✅ Use working implementation in production (recommend: PodMetadata access)

---

**Generated**: October 14, 2025  
**Tested with**: Spring Cloud Kubernetes 3.1.0, Spring Cloud 2023.0.0, Spring Boot 3.2.0

