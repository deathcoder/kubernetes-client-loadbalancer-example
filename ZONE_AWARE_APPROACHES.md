# Zone-Aware Load Balancing Approaches

This document compares the four different approaches to implementing zone-aware load balancing with Spring Cloud Kubernetes.

## Summary of Approaches

| Approach | Complexity | Uses Spring Built-ins | Performance | Maintainability |
|----------|------------|----------------------|-------------|-----------------|
| **1. Custom Client** (client-service) | High | No | Medium | Medium |
| **2. Simple Client** (simple-client-service) | Medium | Partial | High | High |
| **3. Slice Client** (slice-client-service) | Medium | No | High | High |
| **4. Wrapped Client** (wrapped-client-service) | Low | **Yes** | High | **Best** |

---

## 1. Custom Client (client-service)

### How It Works
- Custom `ServiceInstanceListSupplier` that queries Kubernetes API for pod labels by IP address
- Filters instances based on zone extracted from pod labels
- Requires `KubernetesClient` bean

### Implementation
```java
@Bean
public ServiceInstanceListSupplier serviceInstanceListSupplier(
        ConfigurableApplicationContext context, @Value("${ZONE:unknown}") String zone) {
    
    ServiceInstanceListSupplier delegate = ServiceInstanceListSupplier.builder()
        .withDiscoveryClient()
        .build(context);
    
    KubernetesClient kubernetesClient = context.getBean(KubernetesClient.class);
    
    return new CustomZonePreferenceServiceInstanceListSupplier(
        delegate, zone, kubernetesClient);
}
```

### Pros
- ✅ Works with any service discovery
- ✅ Full control over filtering logic
- ✅ Can implement complex zone selection strategies

### Cons
- ❌ High complexity - queries Kubernetes API for each instance
- ❌ Requires explicit `KubernetesClient` configuration
- ❌ Must handle IP-to-pod mapping manually
- ❌ Additional Kubernetes API calls

### Best For
- Complex zone selection logic
- Non-standard zone labeling
- When you need custom fallback strategies

---

## 2. Simple Client (simple-client-service)

### How It Works
- Custom `ServiceInstanceListSupplier` that accesses `DefaultKubernetesServiceInstance.podMetadata()`
- Filters instances based on zone found in pod metadata
- **Reveals Spring Cloud Kubernetes architectural issue**: zone not in `getMetadata()` but in `podMetadata()`

### Implementation
```java
@Bean
public ServiceInstanceListSupplier serviceInstanceListSupplier(
        ConfigurableApplicationContext context) {
    
    ServiceInstanceListSupplier baseSupplier =
        ServiceInstanceListSupplier.builder()
            .withDiscoveryClient()
            .build(context);
    
    // Wrap with custom supplier that accesses podMetadata()
    return new LoggingServiceInstanceListSupplier(baseSupplier, zone);
}
```

```java
// In LoggingServiceInstanceListSupplier
private String getInstanceZoneFromPodMetadata(ServiceInstance instance) {
    if (!(instance instanceof DefaultKubernetesServiceInstance)) {
        return null;
    }
    
    DefaultKubernetesServiceInstance k8sInstance = 
        (DefaultKubernetesServiceInstance) instance;
    Map<String, Map<String, String>> podMetadata = k8sInstance.podMetadata();
    
    if (podMetadata != null && podMetadata.containsKey("labels")) {
        Map<String, String> labels = podMetadata.get("labels");
        return labels.get("topology.kubernetes.io/zone");
    }
    return null;
}
```

### Pros
- ✅ No additional Kubernetes API calls
- ✅ Simpler than custom-client approach
- ✅ Direct access to pod-level metadata
- ✅ No `KubernetesClient` dependency

### Cons
- ❌ Requires casting to `DefaultKubernetesServiceInstance`
- ❌ Still requires custom filtering logic
- ❌ **Highlights Spring Cloud Kubernetes design issue** (zone should be in `getMetadata()`)

### Best For
- Understanding why Spring Cloud's built-in zone preference doesn't work
- When you want to avoid additional Kubernetes API calls
- Testing and debugging zone metadata issues

---

## 3. Slice Client (slice-client-service)

### How It Works
- Custom `ServiceInstanceListSupplier` that queries Kubernetes EndpointSlices API
- Builds an IP-to-zone cache from EndpointSlice topology information
- Filters instances based on the cached zone mappings
- **Most Kubernetes-native approach**

### Implementation
```java
@Bean
public ServiceInstanceListSupplier serviceInstanceListSupplier(
        ConfigurableApplicationContext context) {
    
    ServiceInstanceListSupplier delegate =
        ServiceInstanceListSupplier.builder()
            .withDiscoveryClient()
            .build(context);
    
    KubernetesClient kubernetesClient = context.getBean(KubernetesClient.class);
    String namespace = context.getEnvironment().getProperty(
        "spring.cloud.kubernetes.client.namespace", "default");
    
    return new EndpointSliceZoneServiceInstanceListSupplier(
        delegate, zone, kubernetesClient, namespace);
}
```

```java
// Query EndpointSlices for zone information
EndpointSliceList endpointSliceList = kubernetesClient.discovery().v1()
    .endpointSlices()
    .inNamespace(namespace)
    .withLabel("kubernetes.io/service-name", serviceId)
    .list();

// Build IP to zone cache
for (EndpointSlice slice : endpointSliceList.getItems()) {
    for (Endpoint endpoint : slice.getEndpoints()) {
        String zone = endpoint.getZone();
        for (String ip : endpoint.getAddresses()) {
            ipToZoneCache.put(ip, zone);
        }
    }
}
```

### Pros
- ✅ Uses standard Kubernetes topology information
- ✅ No pod-specific queries needed
- ✅ Scalable (EndpointSlices designed for large deployments)
- ✅ Works with standard Kubernetes zone labels

### Cons
- ❌ Requires EndpointSlice RBAC permissions
- ❌ Must refresh cache periodically
- ❌ More complex than wrapped-client approach

### Best For
- Production environments with large numbers of endpoints
- When you want a Kubernetes-native solution
- Environments with standard Kubernetes zone topology

---

## 4. Wrapped Client (wrapped-client-service) ⭐ **RECOMMENDED**

### How It Works
- Uses `BeanPostProcessor` to intercept `KubernetesInformerDiscoveryClient`
- Wraps it to expose zone from `podMetadata()` in `ServiceInstance.getMetadata()`
- **Enables Spring Cloud's built-in `.withZonePreference()` to work correctly**
- **Fixes the architectural mismatch at the source**

### Implementation
```java
@Configuration
@LoadBalancerClients(defaultConfiguration = LoadBalancerConfiguration.ZoneAwareLoadBalancerConfig.class)
public class LoadBalancerConfiguration implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof KubernetesInformerDiscoveryClient) {
            KubernetesInformerDiscoveryClient client = (KubernetesInformerDiscoveryClient) bean;
            
            return new DiscoveryClient() {
                @Override
                public List<ServiceInstance> getInstances(String serviceId) {
                    return client.getInstances(serviceId).stream()
                        .map(service -> {
                            if (service instanceof DefaultKubernetesServiceInstance) {
                                DefaultKubernetesServiceInstance k8sInstance = 
                                    (DefaultKubernetesServiceInstance) service;
                                
                                // Extract zone from podMetadata
                                String zone = extractZoneFromPodMetadata(k8sInstance);
                                
                                // Return wrapped instance with zone in metadata
                                return new ServiceInstance() {
                                    @Override
                                    public Map<String, String> getMetadata() {
                                        Map<String, String> metadata = 
                                            new HashMap<>(k8sInstance.getMetadata());
                                        
                                        if (zone != null) {
                                            // Add zone where Spring Cloud expects it!
                                            metadata.putIfAbsent("zone", zone);
                                            metadata.putIfAbsent("topology.kubernetes.io/zone", zone);
                                        }
                                        
                                        return metadata;
                                    }
                                    // ... delegate other methods to k8sInstance
                                };
                            }
                            return service;
                        })
                        .toList();
                }
            };
        }
        return bean;
    }
    
    public static class ZoneAwareLoadBalancerConfig {
        @Bean
        public ServiceInstanceListSupplier discoveryClientServiceInstanceListSupplier(
                ConfigurableApplicationContext context) {
            
            // Now we can use Spring Cloud's built-in zone preference!
            return ServiceInstanceListSupplier.builder()
                    .withBlockingDiscoveryClient()
                    .withCaching()
                    .withZonePreference()  // ✨ This now works!
                    .build(context);
        }
    }
}
```

### Pros
- ✅ **Simplest implementation** - no custom filtering logic
- ✅ **Uses Spring Cloud's built-in `.withZonePreference()`**
- ✅ **Fixes the root cause** - zone metadata in the right place
- ✅ **Works for all services automatically** (with `@LoadBalancerClients`)
- ✅ No additional Kubernetes API calls
- ✅ No `KubernetesClient` dependency
- ✅ Easy to maintain and understand
- ✅ Can leverage all Spring Cloud LoadBalancer features (caching, health checks, etc.)

### Cons
- ❌ Relies on `BeanPostProcessor` ordering (minor concern)
- ❌ Specific to Kubernetes discovery client (not portable to other discovery systems)

### Best For
- **Production use** - simplest and most maintainable
- When you want to use Spring Cloud's built-in zone preference
- Applications calling multiple services
- When you want the solution to "just work"

---

## Test Results

All four approaches achieve **100% zone-aware routing**:

### From Zone-A Client
```json
{
  "clientZone": "zone-a",
  "totalCalls": 20,
  "sameZoneCalls": 20,
  "crossZoneCalls": 0,
  "sameZonePercentage": "100.0%"
}
```

### From Zone-B Client
```json
{
  "clientZone": "zone-b",
  "totalCalls": 20,
  "sameZoneCalls": 20,
  "crossZoneCalls": 0,
  "sameZonePercentage": "100.0%"
}
```

---

## Which Approach Should You Use?

### ⭐ **Recommended: Wrapped Client (wrapped-client-service)**

For most production scenarios, use the **Wrapped Client** approach because:

1. **Simplicity**: Leverages Spring Cloud's built-in zone preference
2. **Maintainability**: Minimal custom code, easy to understand
3. **Performance**: No additional Kubernetes API calls
4. **Scalability**: Works for all services with `@LoadBalancerClients(defaultConfiguration=...)`
5. **Future-proof**: Uses standard Spring Cloud LoadBalancer features

### When to Use Others

- **Custom Client**: When you need complex, custom zone selection logic
- **Simple Client**: For learning/debugging the Spring Cloud Kubernetes architecture issue
- **Slice Client**: In very large deployments where EndpointSlices are required for scalability

---

## Key Discovery: Spring Cloud Kubernetes Architecture Issue

**The core problem**: Spring Cloud Kubernetes's `DefaultKubernetesServiceInstance.getMetadata()` doesn't expose pod-level labels (like zone), even though they're available via `podMetadata()`.

**Why it matters**: Spring Cloud LoadBalancer's `.withZonePreference()` expects zone in `getMetadata()`, so it doesn't work out of the box with Kubernetes.

**Solutions**:
1. **Fix at source (Wrapped Client)**: Intercept and expose zone in `getMetadata()` ✅ **BEST**
2. **Work around (Simple/Slice Client)**: Access `podMetadata()` directly and filter manually
3. **Submit PR to Spring Cloud**: Fix `DefaultKubernetesServiceInstance` to include pod labels in `getMetadata()`

---

## Files and Scripts

### Build and Deploy

```bash
# Wrapped Client (Recommended)
./scripts/build-and-deploy-wrapped.sh

# Custom Client
./scripts/build-and-deploy.sh

# Simple Client
./scripts/build-and-deploy-simple.sh

# Slice Client
./scripts/build-and-deploy-slice.sh
```

### Testing

```bash
# Test all deployed clients
./scripts/test-loadbalancing.sh
```

### Source Code

- **Custom Client**: `client-service/src/main/java/com/example/clientservice/`
- **Simple Client**: `simple-client-service/src/main/java/com/example/simpleclient/`
- **Slice Client**: `slice-client-service/src/main/java/com/example/sliceclient/`
- **Wrapped Client**: `wrapped-client-service/src/main/java/com/example/wrappedclient/`

---

## Conclusion

All four approaches work, but the **Wrapped Client** approach is the clear winner for production use:

- ✅ Simplest code
- ✅ Uses Spring Cloud built-ins
- ✅ Most maintainable
- ✅ Best performance
- ✅ Easiest to understand

The other approaches are valuable for:
- **Learning** how Spring Cloud Kubernetes works internally
- **Debugging** zone-aware routing issues
- **Special cases** requiring custom logic or EndpointSlice features

Choose the Wrapped Client for production, and use the others as educational references or for specific edge cases.

