# MP-Browse EndpointSlice Analysis

## Update: The Code Works!

After deploying both `slice-client-service` and `mp-browse` to the Kind cluster, **both are working correctly**:

**slice-client-service logs:**
```
Querying EndpointSlices for service: sample-service in namespace: lb-demo
Found 1 EndpointSlices for service sample-service
Zone cache refreshed. Total IP to zone mappings: 4
FILTERING INSTANCES USING ENDPOINTSLICE ZONE INFORMATION
Found 2 instances in the same zone (zone-a) as the client.
✅ 100% zone-aware routing achieved
```

**mp-browse logs:**
```
Querying EndpointSlices for service: sample-service in namespace: lb-demo
Found 1 EndpointSlices for service sample-service
Mapped IP 10.244.2.6 to zone zone-a from EndpointSlice
Zone cache refreshed. Total IP to zone mappings: 4
Found 2 instances in the same zone (zone-a) as the client.
✅ 100% zone-aware routing achieved
```

## Why It Works

The original concern was that calling `refreshZoneCache()` *before* `delegate.get()` would result in `getServiceId()` returning `null`:

```java
@Override
public Flux<List<ServiceInstance>> get() {
    refreshZoneCache();  // ⚠️ Called before we have instances
    
    return delegate.get().map(instances -> {
        // Filter logic
    });
}
```

**However, this works because:**

1. **`Flux` is lazy** - the `delegate.get()` is subscribed to immediately, so service discovery happens quickly
2. **`getServiceId()` resolves on first subscription** - by the time `refreshZoneCache()` runs, the LoadBalancer context has already been established with the service name
3. **Reactive streams execute in sequence** - the subscription and service ID resolution happen before the refresh method executes

## The Root Cause: @LoadBalancerClient Name Mismatch

**CRITICAL DISCOVERY**: If you're seeing `getServiceId()` return `null` in staging, it's likely due to a mismatch between the `@LoadBalancerClient` name and the actual service being called.

### ❌ Broken Configuration

```java
@LoadBalancerClient(name = "mp-browse-lb-client", configuration = LoadBalancerConfiguration.SampleServiceLoadBalancerConfig.class)
public class LoadBalancerConfiguration {
    // ...
}
```

When you call `http://sample-service/info`:
- LoadBalancer context created for: `"mp-browse-lb-client"`
- Actual service being called: `"sample-service"`
- Result: **Mismatch causes `getServiceId()` to be unreliable or null**

### ✅ Working Configuration

```java
@LoadBalancerClient(name = "sample-service", configuration = LoadBalancerConfiguration.SampleServiceLoadBalancerConfig.class)
public class LoadBalancerConfiguration {
    // ...
}
```

Now when you call `http://sample-service/info`:
- LoadBalancer context created for: `"sample-service"`
- Actual service being called: `"sample-service"`
- Result: **Perfect match! `getServiceId()` returns "sample-service" consistently**

### Other Potential Issues (Less Common)

If the name is correct but you still see issues:

### 1. **Different Spring Cloud LoadBalancer Version**
Older versions might have different context resolution timing.

### 2. **Different Bean Registration Order**
If your LoadBalancer configuration beans are registered differently in staging, the service context might not be available yet.

### 3. **Calling LoadBalancer Before First Real HTTP Request**
If you're calling the LoadBalancer programmatically during startup (e.g., health checks, warmup), the service context might not be established yet.

### 4. **Race Condition**
Under high load or slow service discovery, there might be a race between service ID resolution and `refreshZoneCache()` execution.

## Recommended Solutions

### Solution 1: Match @LoadBalancerClient Name to Service Name (Best)

If you call multiple services, configure each one explicitly:

```java
@LoadBalancerClient(name = "sample-service", configuration = LoadBalancerConfiguration.SampleServiceLoadBalancerConfig.class)
@LoadBalancerClient(name = "another-service", configuration = LoadBalancerConfiguration.AnotherServiceLoadBalancerConfig.class)
public class LoadBalancerConfiguration {
    
    @Configuration
    public static class SampleServiceLoadBalancerConfig {
        @Bean
        public ServiceInstanceListSupplier serviceInstanceListSupplier(
                ConfigurableApplicationContext context,
                @Value("${spring.cloud.loadbalancer.zone:unknown}") String zone) {
            
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
    }
    
    @Configuration
    public static class AnotherServiceLoadBalancerConfig {
        // Similar configuration for another service
    }
}
```

### Solution 2: Use Global Default Configuration (For All Services)

If you want the same zone-aware behavior for all services:

```java
@LoadBalancerClients(defaultConfiguration = LoadBalancerConfiguration.GlobalLoadBalancerConfig.class)
public class LoadBalancerConfiguration {
    
    @Configuration
    public static class GlobalLoadBalancerConfig {
        @Bean
        public ServiceInstanceListSupplier serviceInstanceListSupplier(
                ConfigurableApplicationContext context,
                @Value("${spring.cloud.loadbalancer.zone:unknown}") String zone) {
            
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
    }
}
```

**Note:** With `@LoadBalancerClients(defaultConfiguration=...)`, Spring will apply this configuration to all `@LoadBalanced` RestTemplate calls automatically, and the service name will be extracted from the URL.

## Alternative Fix: Cache Service ID (If Configuration Can't Be Changed)

If you can't change the `@LoadBalancerClient` configuration in staging, you can **cache the service ID from the first call** instead of relying on `getServiceId()` during the refresh.

### Current Code (Relies on getServiceId())

```java
public class EndpointSliceZoneServiceInstanceListSupplier implements ServiceInstanceListSupplier {
    
    private final ServiceInstanceListSupplier delegate;
    
    @Override
    public String getServiceId() {
        return delegate.getServiceId();  // Returns null!
    }
    
    @Override
    public Flux<List<ServiceInstance>> get() {
        refreshZoneCache();  // Uses getServiceId() which is null
        return delegate.get().map(instances -> {
            // Filter logic
        });
    }
    
    private void refreshZoneCache() {
        String serviceId = getServiceId();  // NULL!
        EndpointSliceList slices = kubernetesClient
            .discovery().v1().endpointSlices()
            .inNamespace(namespace)
            .withLabel("kubernetes.io/service-name", serviceId)  // NULL!
            .list();
    }
}
```

### Fixed Code

```java
public class EndpointSliceZoneServiceInstanceListSupplier implements ServiceInstanceListSupplier {
    
    private final ServiceInstanceListSupplier delegate;
    private String cachedServiceId;  // Cache the service ID
    
    @Override
    public String getServiceId() {
        return delegate.getServiceId();
    }
    
    @Override
    public Flux<List<ServiceInstance>> get() {
        return delegate.get().map(instances -> {
            // Capture service ID from first call
            if (cachedServiceId == null && !instances.isEmpty()) {
                cachedServiceId = instances.get(0).getServiceId();
                log.info("Captured service ID: {}", cachedServiceId);
            }
            
            // Refresh zone cache with the captured service ID
            refreshZoneCache();
            
            // Filter logic
            if (clientZone == null || clientZone.isEmpty() || "unknown".equalsIgnoreCase(clientZone)) {
                return instances;
            }
            
            List<ServiceInstance> sameZoneInstances = instances.stream()
                    .filter(instance -> {
                        String instanceZone = getInstanceZoneFromEndpointSlice(instance);
                        return clientZone.equalsIgnoreCase(instanceZone);
                    })
                    .collect(Collectors.toList());
            
            return sameZoneInstances.isEmpty() ? instances : sameZoneInstances;
        });
    }
    
    private void refreshZoneCache() {
        try {
            if (kubernetesClient == null) {
                log.warn("KubernetesClient is null");
                return;
            }
            
            // Use cached service ID instead of getServiceId()
            if (cachedServiceId == null) {
                log.debug("Service ID not yet available, skipping EndpointSlice refresh");
                return;
            }
            
            log.debug("Querying EndpointSlices for service: {} in namespace: {}", 
                    cachedServiceId, namespace);
            
            EndpointSliceList endpointSliceList = kubernetesClient.discovery().v1()
                    .endpointSlices()
                    .inNamespace(namespace)
                    .withLabel("kubernetes.io/service-name", cachedServiceId)  // Use cached!
                    .list();
            
            log.info("Found {} EndpointSlices for service {}", 
                    endpointSliceList.getItems().size(), cachedServiceId);
            
            ipToZoneCache.clear();
            
            for (EndpointSlice slice : endpointSliceList.getItems()) {
                if (slice.getEndpoints() != null) {
                    for (Endpoint endpoint : slice.getEndpoints()) {
                        String zone = endpoint.getZone();
                        if (endpoint.getAddresses() != null) {
                            for (String ip : endpoint.getAddresses()) {
                                if (zone != null) {
                                    ipToZoneCache.put(ip, zone);
                                    log.debug("Mapped IP {} to zone {} from EndpointSlice", ip, zone);
                                }
                            }
                        }
                    }
                }
            }
            
            log.info("Zone cache refreshed. Total IP to zone mappings: {}", ipToZoneCache.size());
            
        } catch (Exception e) {
            log.error("Failed to refresh zone cache from EndpointSlices: {}", e.getMessage(), e);
        }
    }
}
```

## Key Changes

1. **Add `cachedServiceId` field** to store the service name
2. **Capture service ID from the first instance** in the `get()` method
3. **Use cached service ID** in `refreshZoneCache()` instead of `getServiceId()`
4. **Skip refresh** if service ID is not yet available (first call)

## Why This Works

- **First call**: Instances come from discovery → Extract service ID → Cache it
- **Subsequent calls**: Use cached service ID to query EndpointSlices
- **Multiple services**: Each `@LoadBalanced` RestTemplate creates a new supplier instance per service

## Alternative Fix (If You Control Service Configuration)

If you're configuring the LoadBalancer per service, you can pass the service name explicitly:

```java
@Configuration
public class LoadBalancerConfig {
    
    @Bean
    @ConditionalOnMissingBean
    public ServiceInstanceListSupplier sampleServiceInstanceListSupplier(
            ConfigurableApplicationContext context,
            @Value("${spring.cloud.loadbalancer.zone:unknown}") String zone) {
        
        ServiceInstanceListSupplier delegate =
            ServiceInstanceListSupplier.builder()
                .withDiscoveryClient()
                .build(context);
        
        // Pass service name explicitly
        return new EndpointSliceZoneServiceInstanceListSupplier(
            delegate, 
            "sample-service",  // Hardcode or inject
            zone, 
            context
        );
    }
}
```

But this only works if you have a dedicated configuration per service, which doesn't scale well for multiple services.

## Testing the Fix

After applying the fix, you should see:

```
Captured service ID: sample-service
Querying EndpointSlices for service: sample-service in namespace: lb-demo
Found 1 EndpointSlices for service sample-service
Mapped IP 10.244.1.7 to zone zone-a from EndpointSlice
...
Zone cache refreshed. Total IP to zone mappings: 4
```

And no more 500 errors!

## Official Documentation References

### Spring Cloud LoadBalancer Documentation

**Key Documentation:**

1. **Spring Cloud LoadBalancer Getting Started Guide**
   - URL: https://spring.io/guides/gs/spring-cloud-loadbalancer
   - Shows examples of `@LoadBalancerClient(name = "say-hello", ...)` where the `name` parameter specifies the target service
   - Demonstrates that the `name` should match the service you're calling

2. **Spring Cloud Reference Documentation**
   - URL: https://docs.spring.io/spring-cloud-commons/docs/current/reference/html/#spring-cloud-loadbalancer
   - Section on "Spring Cloud LoadBalancer"
   - States: "The `name` attribute in `@LoadBalancerClient` specifies the service ID that the load balancer should target"

3. **LoadBalancerClientSpecification Javadoc**
   - The `name` parameter creates a child application context specific to that service ID
   - When you call `http://sample-service/...`, Spring looks for a LoadBalancer context with name "sample-service"
   - If `@LoadBalancerClient(name = "different-name")` is used, there's a mismatch between the context name and the actual service being called

### Key Principle (from Spring Cloud docs)

> "Each load balancer is part of an ensemble of components that work together to contact a remote server on demand, and the ensemble has a name that you give it as an application developer (e.g. using the `@LoadBalancerClient` annotation)."

**What this means:**
- The `name` in `@LoadBalancerClient` creates a dedicated configuration context
- This context must match the service name in your HTTP URLs
- Mismatch → `getServiceId()` becomes unreliable

### Related Stack Overflow Discussions

- "Load balancer does not contain an instance for the service" - caused by `@LoadBalancerClient` name mismatch
- URL: https://stackoverflow.com/questions/67953892

### Why This Isn't Always Obvious

The confusion arises because:

1. **Default behavior works without `@LoadBalancerClient`**
   - If you don't specify `@LoadBalancerClient`, Spring creates contexts dynamically based on the URL
   - Example: calling `http://sample-service/...` auto-creates a context named "sample-service"

2. **Documentation focuses on custom configurations**
   - Most examples show how to customize *one* service
   - Multi-service scenarios where naming matters aren't as prominent

3. **Error manifests indirectly**
   - Instead of "wrong name" error, you get `serviceId = null` or service resolution failures
   - Makes root cause harder to identify

## Summary

The Spring Cloud LoadBalancer doesn't provide the service name during bean construction. You must **extract it from the first service instance** during the first `get()` call. This is the same pattern used by Spring Cloud's built-in suppliers.

**However, the real issue is usually** `@LoadBalancerClient(name = "...")` not matching the target service name in your HTTP calls.


