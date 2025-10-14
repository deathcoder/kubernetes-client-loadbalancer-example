# Quick Reference - Zone-Aware Load Balancing

## Quick Start

```bash
# Setup local Kind cluster with zones
./scripts/setup-kind-cluster.sh

# Build and deploy
./scripts/build-and-deploy.sh

# Test zone-aware routing
./scripts/test-loadbalancing.sh
```

**Expected Result:** 100% same-zone traffic

---

## Key Files

| File | Purpose |
|------|---------|
| `CustomZonePreferenceServiceInstanceListSupplier.java` | Core zone-filtering logic |
| `LoadBalancerConfig.java` | Registers custom supplier per service |
| `KubernetesClientConfig.java` | Provides KubernetesClient bean |
| `application.yml` | Configures POD mode and zone |
| `k8s/*.yaml` | Kubernetes deployments with zone labels |

---

## Core Implementation

### 1. Query Pods by IP (Not Name)

```java
String podIp = instance.getHost();  // e.g., "10.244.1.20"

List<Pod> pods = kubernetesClient.pods()
    .inNamespace(namespace)
    .list()
    .getItems();

Pod matchingPod = pods.stream()
    .filter(pod -> podIp.equals(pod.getStatus().getPodIP()))
    .findFirst()
    .orElse(null);
```

### 2. Extract Zone from Pod Labels

```java
String zone = matchingPod.getMetadata()
    .getLabels()
    .get("topology.kubernetes.io/zone");
// or
String zone = matchingPod.getMetadata()
    .getLabels()
    .get("zone");
```

### 3. Filter Instances by Zone

```java
List<ServiceInstance> sameZoneInstances = instances.stream()
    .filter(instance -> clientZone.equals(getInstanceZone(instance)))
    .collect(Collectors.toList());

// Fallback to all instances if none in same zone
return sameZoneInstances.isEmpty() ? instances : sameZoneInstances;
```

---

## Configuration Checklist

### Application Configuration (`application.yml`)

- ✅ `spring.cloud.kubernetes.loadbalancer.mode: POD`
- ✅ `spring.cloud.loadbalancer.zone: ${ZONE}`
- ✅ `spring.cloud.kubernetes.discovery.metadata.add-pod-labels: true`

### Kubernetes Deployment

- ✅ Pod label: `zone: zone-a`
- ✅ Environment variable: `ZONE=zone-a`
- ✅ ServiceAccount with RBAC permissions
- ✅ Node affinity for zone placement

### Maven Configuration

- ✅ `spring-cloud-starter-kubernetes-client-loadbalancer`
- ✅ `spring-boot-starter-webflux` (for ReactiveDiscoveryClient)
- ✅ `io.fabric8:kubernetes-client`
- ✅ `maven.compiler.parameters=true`

---

## RBAC Requirements

```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: spring-cloud-kubernetes
---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRole
metadata:
  name: spring-cloud-kubernetes-role
rules:
- apiGroups: [""]
  resources: ["pods", "services", "endpoints"]
  verbs: ["get", "list", "watch"]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRoleBinding
metadata:
  name: spring-cloud-kubernetes-role-binding
subjects:
- kind: ServiceAccount
  name: spring-cloud-kubernetes
roleRef:
  kind: ClusterRole
  name: spring-cloud-kubernetes-role
  apiGroup: rbac.authorization.k8s.io
```

---

## Troubleshooting

### Issue: Still seeing cross-zone traffic

**Check:**
1. Pod labels match expected zone labels
2. Environment variable `ZONE` is correctly set
3. Logs show "Successfully fetched zone from Kubernetes"
4. `KubernetesClient` is not null

**Debug:**
```bash
kubectl logs -n lb-demo <pod-name> | grep -i zone
```

### Issue: KubernetesClient is null

**Solution:** Ensure `KubernetesClientConfig` bean is configured and accessed from parent context:
```java
if (context.getParent() != null) {
    kubernetesClient = context.getParent().getBean(KubernetesClient.class);
}
```

### Issue: No zone found for instance

**Check:**
1. Pod labels are correctly set in deployment
2. RBAC permissions allow listing/getting pods
3. IP-based pod lookup is working

**Debug:**
```bash
kubectl get pods -n lb-demo --show-labels
kubectl get pods -n lb-demo -o wide  # Check IPs
```

### Issue: IllegalArgumentException about parameter names

**Solution:** Add to `pom.xml`:
```xml
<properties>
    <maven.compiler.parameters>true</maven.compiler.parameters>
</properties>
```

---

## Testing Commands

### Check Pod Distribution
```bash
kubectl get pods -n lb-demo -o wide --show-labels
```

### Test Load Balancing
```bash
./scripts/test-loadbalancing.sh
```

### Check Logs
```bash
# Check client logs
kubectl logs -n lb-demo -l app=client-service --tail=50

# Check for zone fetching
kubectl logs -n lb-demo <pod-name> | grep "Successfully fetched"

# Check for errors
kubectl logs -n lb-demo <pod-name> | grep -i error
```

### Port Forward for Manual Testing
```bash
kubectl port-forward -n lb-demo svc/client-service 8081:8081
curl "http://localhost:8081/test-loadbalancing?calls=50"
```

---

## Key Metrics to Monitor

| Metric | Target | Alert If |
|--------|--------|----------|
| Same-zone traffic % | 100% | < 95% |
| Cross-zone fallback rate | 0% | > 5% |
| Kubernetes API query time | < 50ms | > 200ms |
| Load balancer cache hit rate | > 95% | < 80% |

---

## Performance Tuning

### Cache TTL
```yaml
spring:
  cloud:
    loadbalancer:
      cache:
        ttl: 35s  # Default is 35 seconds
        capacity: 256  # Max cache entries
```

### Health Check Configuration
```yaml
spring:
  cloud:
    loadbalancer:
      health-check:
        interval: 25s  # How often to check instance health
```

---

## Common Patterns

### Using with RestTemplate
```java
@Configuration
public class RestTemplateConfig {
    
    @Bean
    @LoadBalanced  // Enables zone-aware load balancing
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}

@Service
public class MyService {
    
    @Autowired
    private RestTemplate restTemplate;
    
    public String callOtherService() {
        // Automatically routes to same-zone instances
        return restTemplate.getForObject(
            "http://sample-service/api/endpoint",
            String.class
        );
    }
}
```

### Using with WebClient
```java
@Configuration
public class WebClientConfig {
    
    @Bean
    @LoadBalanced
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}

@Service
public class MyService {
    
    @Autowired
    private WebClient.Builder webClientBuilder;
    
    public Mono<String> callOtherService() {
        return webClientBuilder.build()
            .get()
            .uri("http://sample-service/api/endpoint")
            .retrieve()
            .bodyToMono(String.class);
    }
}
```

---

## Production Deployment Checklist

- [ ] Zone labels on all nodes: `topology.kubernetes.io/zone`
- [ ] Pod labels match zone deployment
- [ ] Environment variable `ZONE` set in all deployments
- [ ] ServiceAccount with proper RBAC attached
- [ ] Maven compiler parameters enabled
- [ ] All required dependencies in `pom.xml`
- [ ] Health checks configured
- [ ] Monitoring and alerting set up for cross-zone traffic
- [ ] Performance testing completed
- [ ] Fallback behavior tested (all same-zone instances down)

---

## What Spring Cloud Kubernetes Doesn't Do (That We Handle)

❌ **Automatic zone metadata in POD mode**  
✅ We query Kubernetes API directly

❌ **IP-to-pod-name resolution**  
✅ We list all pods and match by IP

❌ **Zone-aware filtering out of the box**  
✅ We implement custom `ServiceInstanceListSupplier`

❌ **KubernetesClient in load balancer context**  
✅ We access it from parent context

---

## Version Compatibility

Tested with:
- Spring Boot: 3.2.0
- Spring Cloud: 2023.0.0
- Spring Cloud Kubernetes: 3.1.0
- Java: 17
- Kubernetes: 1.27+
- Kind: 0.20+

---

## Additional Resources

- Full solution: `SOLUTION.md`
- Project README: `README.md`
- Example outputs: `EXAMPLES.md`
- Setup scripts: `scripts/`

