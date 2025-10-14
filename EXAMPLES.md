# Usage Examples

## Example 1: Basic RestTemplate Call with Load Balancing

```java
@Service
public class MyService {
    
    @Autowired
    private RestTemplate restTemplate;  // @LoadBalanced
    
    public UserData getUserData(String userId) {
        // Uses service name instead of host:port
        String url = "http://sample-service/users/" + userId;
        return restTemplate.getForObject(url, UserData.class);
    }
}
```

## Example 2: Testing Different Zone Scenarios

### Scenario A: Client in zone-a calls service

```bash
# Port forward to zone-a client
./scripts/port-forward.sh client-service zone-a

# In another terminal, make calls
for i in {1..10}; do
  curl -s http://localhost:8081/call-service | jq '.serviceResponse.zone'
done
```

Expected: All responses show `zone-a`

### Scenario B: Client in zone-b calls service

```bash
# Port forward to zone-b client
./scripts/port-forward.sh client-service zone-b

# In another terminal, make calls
for i in {1..10}; do
  curl -s http://localhost:8081/call-service | jq '.serviceResponse.zone'
done
```

Expected: All responses show `zone-b`

## Example 3: Simulating Zone Failure

### Scale down all zone-a services

```bash
kubectl scale deployment sample-service-zone-a -n lb-demo --replicas=0

# Wait a moment, then test from zone-a client
./scripts/port-forward.sh client-service zone-a

# Make calls - they should now go to zone-b (fallback)
curl http://localhost:8081/test-loadbalancing?calls=10
```

Expected: Traffic falls back to zone-b when zone-a is unavailable

### Restore zone-a services

```bash
kubectl scale deployment sample-service-zone-a -n lb-demo --replicas=2
```

## Example 4: Custom Load Balancing Logic

Add a custom load balancer configuration for weighted routing:

```java
@Configuration
public class CustomLoadBalancerConfig {
    
    @Value("${spring.cloud.loadbalancer.zone:}")
    private String zone;
    
    @Bean
    public ServiceInstanceListSupplier customServiceInstanceListSupplier(
            ConfigurableApplicationContext context) {
        
        ServiceInstanceListSupplier delegate = 
            ServiceInstanceListSupplier.builder()
                .withDiscoveryClient()
                .build(context);
        
        // Add health checks
        ServiceInstanceListSupplier healthCheckDelegate = 
            new HealthCheckServiceInstanceListSupplier(delegate, context);
        
        // Add zone preference
        LoadBalancerZoneConfig zoneConfig = new LoadBalancerZoneConfig(zone);
        return new ZonePreferenceServiceInstanceListSupplier(
            healthCheckDelegate, zoneConfig);
    }
}
```

## Example 5: Adding Custom Headers

Modify the RestTemplate to add custom headers:

```java
@Configuration
public class RestTemplateConfig {

    @Bean
    @LoadBalanced
    public RestTemplate restTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        
        // Add interceptor for custom headers
        restTemplate.setInterceptors(Collections.singletonList(
            (request, body, execution) -> {
                request.getHeaders().add("X-Request-Source", "client-service");
                request.getHeaders().add("X-Zone", System.getenv("ZONE"));
                return execution.execute(request, body);
            }
        ));
        
        return restTemplate;
    }
}
```

## Example 6: Monitoring Load Balancer Decisions

Enable detailed logging to see load balancer decisions:

```yaml
# In application.yml
logging:
  level:
    org.springframework.cloud.kubernetes.fabric8.discovery: DEBUG
    org.springframework.cloud.loadbalancer: TRACE
    org.springframework.cloud.kubernetes.client.discovery: DEBUG
```

Then check logs:

```bash
./scripts/logs.sh client-service zone-a | grep -i "load\|zone\|instance"
```

## Example 7: Adding Resilience with Circuit Breaker

Add Resilience4j for circuit breaking:

```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-circuitbreaker-resilience4j</artifactId>
</dependency>
```

```java
@Service
public class ResilientService {
    
    @Autowired
    private RestTemplate restTemplate;
    
    @Autowired
    private CircuitBreakerFactory circuitBreakerFactory;
    
    public Map<String, Object> callServiceWithCircuitBreaker() {
        CircuitBreaker circuitBreaker = circuitBreakerFactory.create("sample-service");
        
        return circuitBreaker.run(
            () -> restTemplate.getForObject("http://sample-service/info", Map.class),
            throwable -> getFallbackResponse()
        );
    }
    
    private Map<String, Object> getFallbackResponse() {
        Map<String, Object> fallback = new HashMap<>();
        fallback.put("status", "fallback");
        fallback.put("message", "Service temporarily unavailable");
        return fallback;
    }
}
```

## Example 8: Testing with Different Replica Counts

### High availability configuration

```bash
# Scale up for high availability
kubectl scale deployment sample-service-zone-a -n lb-demo --replicas=5
kubectl scale deployment sample-service-zone-b -n lb-demo --replicas=5

# Test distribution
curl http://localhost:8081/test-loadbalancing?calls=50 | jq '.podDistribution'
```

### Minimal configuration

```bash
# Scale down to 1 per zone
kubectl scale deployment sample-service-zone-a -n lb-demo --replicas=1
kubectl scale deployment sample-service-zone-b -n lb-demo --replicas=1

# Test that it still works
curl http://localhost:8081/test-loadbalancing?calls=10
```

## Example 9: Performance Testing

Test the performance with parallel requests:

```bash
# Port forward first
./scripts/port-forward.sh client-service zone-a

# Run parallel requests with Apache Bench
ab -n 1000 -c 10 http://localhost:8081/call-service

# Or with curl in parallel
seq 1 100 | xargs -P 10 -I {} curl -s http://localhost:8081/call-service > /dev/null
```

## Example 10: Integration with Different Services

Add multiple services with zone-aware routing:

```yaml
# k8s/order-service.yaml
---
apiVersion: v1
kind: Service
metadata:
  name: order-service
  namespace: lb-demo
spec:
  selector:
    app: order-service
  ports:
    - port: 8082
---
# Similar deployments for zone-a and zone-b
```

Then call from client:

```java
@RestController
public class MultiServiceController {
    
    @Autowired
    private RestTemplate restTemplate;
    
    @GetMapping("/complex-operation")
    public Map<String, Object> complexOperation() {
        // All these calls will be zone-aware
        Map userInfo = restTemplate.getForObject("http://user-service/info", Map.class);
        Map orderInfo = restTemplate.getForObject("http://order-service/orders", Map.class);
        Map inventoryInfo = restTemplate.getForObject("http://inventory-service/stock", Map.class);
        
        return Map.of(
            "user", userInfo,
            "orders", orderInfo,
            "inventory", inventoryInfo
        );
    }
}
```

## Example 11: Debugging Service Discovery

Execute commands inside a pod to debug:

```bash
# Get into a client pod
kubectl exec -it -n lb-demo \
  $(kubectl get pod -n lb-demo -l app=client-service,zone=zone-a -o jsonpath='{.items[0].metadata.name}') \
  -- sh

# Inside the pod:
# Check DNS resolution
nslookup sample-service

# Check endpoints
wget -O- http://sample-service:8080/info

# Check environment variables
env | grep KUBERNETES
```

## Example 12: Custom Metrics

Add metrics to track zone-aware routing:

```java
@Component
public class LoadBalancerMetrics {
    
    private final MeterRegistry registry;
    
    public LoadBalancerMetrics(MeterRegistry registry) {
        this.registry = registry;
    }
    
    public void recordCall(String targetZone, boolean sameZone) {
        Counter.builder("loadbalancer.calls")
            .tag("target.zone", targetZone)
            .tag("same.zone", String.valueOf(sameZone))
            .register(registry)
            .increment();
    }
}
```

Then expose metrics:

```bash
curl http://localhost:8081/actuator/metrics/loadbalancer.calls
```

---

## 🎯 Next Steps

After trying these examples:

1. **Adapt to your use case** - Replace sample-service with your actual services
2. **Add more zones** - Extend the cluster setup to include zone-c, zone-d, etc.
3. **Test failure scenarios** - Kill pods, scale to zero, network partitions
4. **Measure performance** - Compare zone-aware vs random load balancing
5. **Deploy to real cluster** - Use these same concepts in your production environment

## 📚 More Resources

- [Spring Cloud LoadBalancer Docs](https://docs.spring.io/spring-cloud-commons/docs/current/reference/html/#spring-cloud-loadbalancer)
- [Kubernetes Topology Keys](https://kubernetes.io/docs/reference/labels-annotations-taints/#topologykubernetesiozone)
- [Circuit Breaker Pattern](https://resilience4j.readme.io/docs/circuitbreaker)

