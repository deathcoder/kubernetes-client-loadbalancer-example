# Kubernetes Load Balancer Demo with Zone-Aware Routing

This project demonstrates **Spring Cloud Kubernetes LoadBalancer** with **zone-aware routing** using RestTemplate. It includes a complete local test environment using Kind (Kubernetes in Docker) that significantly speeds up the development loop.

## 🎯 What This Demo Shows

- ✅ Spring Cloud Kubernetes LoadBalancer with `@LoadBalanced` RestTemplate
- ✅ Zone-aware load balancing (traffic stays within the same availability zone)
- ✅ Local Kubernetes cluster with simulated zones
- ✅ Fast development loop with quick rebuild scripts
- ✅ Multiple service instances across different zones
- ✅ Testing endpoints to verify load balancing behavior

## 📋 Prerequisites

Before starting, ensure you have the following installed:

```bash
# Required
- Java 17+
- Maven 3.6+
- Docker Desktop for Mac
- kubectl
- kind (Kubernetes in Docker)
- jq (for JSON formatting in tests)

# Install missing tools with Homebrew
brew install kubectl kind jq maven
```

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────┐
│                     Kind Cluster                         │
│                                                          │
│  ┌──────────────────┐        ┌──────────────────┐      │
│  │    Zone A        │        │    Zone B        │      │
│  │                  │        │                  │      │
│  │ ┌──────────────┐ │        │ ┌──────────────┐ │      │
│  │ │ Client A     │ │        │ │ Client B     │ │      │
│  │ │ (zone-a)     │ │        │ │ (zone-b)     │ │      │
│  │ └──────┬───────┘ │        │ └──────┬───────┘ │      │
│  │        │         │        │        │         │      │
│  │        ▼         │        │        ▼         │      │
│  │ ┌──────────────┐ │        │ ┌──────────────┐ │      │
│  │ │ Service A1   │ │        │ │ Service B1   │ │      │
│  │ │ Service A2   │ │        │ │ Service B2   │ │      │
│  │ └──────────────┘ │        │ └──────────────┘ │      │
│  └──────────────────┘        └──────────────────┘      │
│                                                          │
│  Client in zone-a → Prefers Service A1/A2 (same zone)  │
│  Client in zone-b → Prefers Service B1/B2 (same zone)  │
└─────────────────────────────────────────────────────────┘
```

### Components

1. **Sample Service** - A simple REST service that returns information about itself (pod name, zone, IP)
2. **Client Service** - Uses `@LoadBalanced` RestTemplate to call sample-service with zone-aware load balancing
3. **Kind Cluster** - Local Kubernetes cluster with nodes labeled as different zones

## 🚀 Quick Start

### Step 1: Setup Local Kubernetes Cluster

Create a local Kind cluster with simulated availability zones:

```bash
./scripts/setup-kind-cluster.sh
```

This will:
- Create a Kind cluster with 3 worker nodes
- Label nodes with zone information (zone-a, zone-b)
- Setup the cluster for zone-aware routing

### Step 2: Build and Deploy Applications

Build the applications and deploy them to the cluster:

```bash
./scripts/build-and-deploy.sh
```

This will:
- Build both services with Maven
- Create Docker images
- Load images into the Kind cluster
- Deploy all Kubernetes resources
- Wait for all pods to be ready

### Step 3: Test Zone-Aware Load Balancing

Run the automated test:

```bash
./scripts/test-loadbalancing.sh
```

This will:
- Make 20 calls from the zone-a client
- Make 20 calls from the zone-b client
- Show the distribution of calls and zone preferences

**Expected Result:** You should see that clients primarily call services in their own zone (close to 100% same-zone calls).

## 🧪 Manual Testing

### Port Forward to Access Services

```bash
# Access client service from zone-a
./scripts/port-forward.sh client-service zone-a

# In another terminal, access client service from zone-b
./scripts/port-forward.sh client-service zone-b
```

### Test Endpoints

Once port-forwarded, you can access:

```bash
# Get client info (shows which zone the client is in)
curl http://localhost:8081/client-info

# Make a single call to the sample service
curl http://localhost:8081/call-service

# Test load balancing with 50 calls
curl http://localhost:8081/test-loadbalancing?calls=50 | jq '.'
```

### Sample Response

```json
{
  "clientZone": "zone-a",
  "clientPod": "client-service-zone-a-xxx",
  "totalCalls": 50,
  "sameZoneCalls": 50,
  "crossZoneCalls": 0,
  "sameZonePercentage": "100.0%",
  "podDistribution": {
    "sample-service-zone-a-xxx-1": 25,
    "sample-service-zone-a-xxx-2": 25
  },
  "zoneDistribution": {
    "zone-a": 50
  }
}
```

## ⚡ Fast Development Loop

The `dev-rebuild.sh` script provides a fast development loop:

```bash
# Rebuild and redeploy just the client service
./scripts/dev-rebuild.sh client-service

# Rebuild and redeploy just the sample service
./scripts/dev-rebuild.sh sample-service

# Rebuild and redeploy everything
./scripts/dev-rebuild.sh all
```

This script:
- Builds only the changed service
- Creates a new Docker image
- Loads it into Kind
- Performs a rolling restart
- Takes ~30-60 seconds instead of several minutes

## 🔍 Debugging and Logs

### View Logs

```bash
# View client-service logs from zone-a
./scripts/logs.sh client-service zone-a

# View sample-service logs from zone-b
./scripts/logs.sh sample-service zone-b
```

### Check Pod Distribution

```bash
kubectl get pods -n lb-demo -L zone,topology.kubernetes.io/zone -o wide
```

### Check Service Discovery

```bash
# Exec into a client pod
kubectl exec -it -n lb-demo $(kubectl get pod -n lb-demo -l app=client-service,zone=zone-a -o jsonpath='{.items[0].metadata.name}') -- sh

# Inside the pod, check service endpoints
nslookup sample-service
```

## 📝 How It Works

### Zone-Aware Load Balancing Configuration

The key configuration is in `client-service/src/main/resources/application.yml`:

```yaml
spring:
  cloud:
    kubernetes:
      loadbalancer:
        mode: POD  # Use POD mode for zone-aware load balancing
    loadbalancer:
      zone: ${ZONE:unknown}  # Zone preference
```

And the load balancer configuration in `LoadBalancerConfig.java`:

```java
@Bean
public ServiceInstanceListSupplier zonePreferenceServiceInstanceListSupplier(
        ConfigurableApplicationContext context) {
    
    ServiceInstanceListSupplier delegate = 
        ServiceInstanceListSupplier.builder()
            .withDiscoveryClient()
            .build(context);
    
    LoadBalancerZoneConfig zoneConfig = new LoadBalancerZoneConfig(zone);
    return new ZonePreferenceServiceInstanceListSupplier(delegate, zoneConfig);
}
```

### How Zones Are Detected

1. **Pod Labels**: Each pod has a label `topology.kubernetes.io/zone` set to its zone
2. **Environment Variable**: The `ZONE` environment variable is passed to the container
3. **Spring Cloud LoadBalancer**: Uses the `spring.cloud.loadbalancer.zone` property to prefer instances in the same zone

### RestTemplate with Load Balancing

```java
@Bean
@LoadBalanced
public RestTemplate restTemplate() {
    return new RestTemplate();
}

// Usage in controller
String url = "http://sample-service/info";  // Service name instead of host:port
Map<String, String> response = restTemplate.getForObject(url, Map.class);
```

The `@LoadBalanced` annotation enables:
- Service discovery via Kubernetes
- Client-side load balancing
- Zone-aware routing when configured

## 🔧 Project Structure

```
kubernetes-loadbalancer/
├── sample-service/              # The service being called
│   ├── src/main/java/
│   │   └── com/example/sampleservice/
│   │       ├── SampleServiceApplication.java
│   │       └── controller/
│   │           └── InfoController.java
│   ├── Dockerfile
│   └── pom.xml
├── client-service/              # The client with load balancing
│   ├── src/main/java/
│   │   └── com/example/clientservice/
│   │       ├── ClientServiceApplication.java
│   │       ├── config/
│   │       │   ├── RestTemplateConfig.java
│   │       │   └── LoadBalancerConfig.java
│   │       └── controller/
│   │           └── TestController.java
│   ├── Dockerfile
│   └── pom.xml
├── k8s/                         # Kubernetes manifests
│   ├── namespace.yaml
│   ├── rbac.yaml
│   ├── sample-service.yaml
│   └── client-service.yaml
├── scripts/                     # Helper scripts
│   ├── setup-kind-cluster.sh
│   ├── build-and-deploy.sh
│   ├── test-loadbalancing.sh
│   ├── dev-rebuild.sh
│   ├── port-forward.sh
│   ├── logs.sh
│   └── cleanup.sh
└── pom.xml                      # Parent POM
```

## 🧹 Cleanup

When you're done, clean up the Kind cluster:

```bash
./scripts/cleanup.sh
```

## 📚 Key Dependencies

From the [Spring Cloud Kubernetes Documentation](https://docs.spring.io/spring-cloud-kubernetes/reference/load-balancer.html):

```xml
<!-- For Kubernetes Java Client Implementation -->
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-kubernetes-client-loadbalancer</artifactId>
</dependency>

<!-- Required for ReactiveDiscoveryClient -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
```

**Important**: The `spring-boot-starter-webflux` dependency is required to provide the `ReactiveDiscoveryClient` bean that Spring Cloud LoadBalancer uses for service discovery, even in non-reactive applications.

## 🎓 Learning Points

1. **POD vs SERVICE Mode**
   - POD mode: Uses DiscoveryClient to find pod endpoints (enables zone-aware routing)
   - SERVICE mode: Uses Kubernetes service DNS (simpler but no zone awareness)

2. **Zone Preference**
   - Spring Cloud LoadBalancer checks the `spring.cloud.loadbalancer.zone` property
   - Matches it against the `topology.kubernetes.io/zone` label on pods
   - Prefers pods in the same zone but can fall back to other zones if needed

3. **Fast Development**
   - Kind allows running Kubernetes locally without cloud resources
   - Image loading into Kind is much faster than pushing to a registry
   - Rolling restarts allow testing without full redeployment

## 🐛 Troubleshooting

### Application fails to start: "ReactiveDiscoveryClient required"

**Error:**
```
required a bean of type 'org.springframework.cloud.client.discovery.ReactiveDiscoveryClient' that could not be found
```

**Solution:** Add the `spring-boot-starter-webflux` dependency to your `pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-webflux</artifactId>
</dependency>
```

This is required because Spring Cloud LoadBalancer uses reactive components internally, even in non-reactive applications.

### Pods not starting

```bash
# Check pod status
kubectl get pods -n lb-demo

# Check logs
kubectl logs -n lb-demo <pod-name>

# Describe pod for events
kubectl describe pod -n lb-demo <pod-name>
```

### Load balancing not working

```bash
# Check if discovery is enabled
kubectl exec -n lb-demo <client-pod> -- env | grep KUBERNETES

# Verify RBAC permissions
kubectl get clusterrolebinding spring-cloud-kubernetes-role-binding

# Check Spring Cloud logs
kubectl logs -n lb-demo <client-pod> | grep "LoadBalancer\|Discovery"
```

### All traffic going to one zone

- Verify pod labels: `kubectl get pods -n lb-demo -L topology.kubernetes.io/zone`
- Check ZONE environment variable in pods
- Ensure `ZonePreferenceServiceInstanceListSupplier` is configured

### Deployments show "unchanged" after rebuild

**Issue:** After rebuilding and redeploying, Kubernetes doesn't pick up the new images.

**Cause:** When using the `latest` tag with local Kind images, Kubernetes doesn't detect image changes.

**Solution 1 - Automatic (Recommended):**
The `build-and-deploy.sh` script now automatically restarts deployments after loading new images.

**Solution 2 - Manual restart:**
```bash
# Force restart all deployments
./scripts/restart-deployments.sh

# Or restart individual services
kubectl rollout restart deployment/client-service-zone-a -n lb-demo
kubectl rollout restart deployment/client-service-zone-b -n lb-demo
```

**Solution 3 - Delete and recreate pods:**
```bash
kubectl delete pods -n lb-demo -l app=client-service
kubectl delete pods -n lb-demo -l app=sample-service
```

**Note:** The manifests use `imagePullPolicy: Never` for local Kind development, which tells Kubernetes to only use images already present in the cluster.

## 📖 Additional Resources

- [Spring Cloud Kubernetes Load Balancer Documentation](https://docs.spring.io/spring-cloud-kubernetes/reference/load-balancer.html)
- [Spring Cloud Load Balancer](https://docs.spring.io/spring-cloud-commons/docs/current/reference/html/#spring-cloud-loadbalancer)
- [Kind Documentation](https://kind.sigs.k8s.io/)

## 💡 Tips for Your Production Setup

1. **Service Mesh Alternative**: Consider Istio or Linkerd for production zone-aware routing
2. **Metrics**: Add Prometheus metrics to track cross-zone traffic
3. **Fallback Strategy**: Configure fallback behavior when no same-zone instances are available
4. **Health Checks**: Ensure proper health checks to avoid routing to unhealthy pods
5. **Testing**: Use this local setup to test zone failure scenarios

## 🤝 Contributing

Feel free to modify and extend this demo for your needs. Common extensions:
- Add circuit breakers with Resilience4j
- Add tracing with Spring Cloud Sleuth
- Implement weighted load balancing
- Add chaos engineering tests (kill pods in one zone)

---

**Happy coding!** 🚀

If you have questions or improvements, feel free to open an issue or PR.

