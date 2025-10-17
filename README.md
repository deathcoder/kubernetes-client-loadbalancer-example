# Kubernetes Load Balancer Demo with Zone-Aware Routing

This project demonstrates **zone-aware load balancing** with Spring Cloud Kubernetes and provides **three working implementations** after discovering that the built-in `ZonePreferenceServiceInstanceListSupplier` doesn't work with Kubernetes Discovery.

## 🚨 Key Finding

**Spring Cloud's built-in zone preference doesn't work with Spring Cloud Kubernetes Discovery** because zone information is stored in `podMetadata()` but the built-in mechanism only checks `getMetadata()`. 

This repository provides:
- ✅ **Three working implementations** achieving 100% zone-aware routing
- ✅ Complete local test environment using Kind
- ✅ Detailed documentation of the issue and workarounds
- ✅ Ready-to-submit GitHub issue for Spring Cloud team

See `SPRING_CLOUD_ISSUE.md` and `FINDINGS_SUMMARY.md` for complete details.

## 🎯 What This Demo Shows

- ✅ Three different working approaches for zone-aware load balancing
- ✅ Spring Cloud Kubernetes LoadBalancer with `@LoadBalanced` RestTemplate
- ✅ Local Kubernetes cluster with simulated availability zones
- ✅ Fast development loop with quick rebuild scripts
- ✅ Comprehensive testing comparing all implementations
- ✅ Detailed investigation of why built-in zone preference fails

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

#### Sample Service (`sample-service/`)
A simple REST service that provides information about itself:
- Returns pod name, zone, IP address
- Deployed with 2 replicas in zone-a and 2 replicas in zone-b (4 total instances)
- Exposes `/info` endpoint for testing

#### Client Services (Three Different Implementations)

This project includes **three different client implementations** to demonstrate various approaches for achieving zone-aware load balancing:

1. **Custom Client** (`client-service/`) - ✅ **100% Zone-Aware**
   - **Approach**: Queries Kubernetes API for pod labels by IP address
   - **How**: Uses `KubernetesClient` to fetch zone from pod labels directly
   - **Pros**: Works perfectly, uses standard `topology.kubernetes.io/zone` label
   - **Cons**: Requires extra API calls per instance

2. **Simple Client** (`simple-client-service/`) - ✅ **100% Zone-Aware** 
   - **Approach**: Accesses `DefaultKubernetesServiceInstance.podMetadata()` directly
   - **How**: Custom supplier that reads zone from the pod metadata structure
   - **Pros**: No extra API calls, uses existing discovery data, cleanest approach
   - **Cons**: Requires Kubernetes-specific code
   - **Note**: This demonstrates the fix for Spring Cloud's built-in zone preference

3. **Slice Client** (`slice-client-service/`) - ✅ **100% Zone-Aware**
   - **Approach**: Uses Kubernetes EndpointSlices API for zone information
   - **How**: Queries EndpointSlices which have native zone support
   - **Pros**: Kubernetes-native approach, future-proof (EndpointSlices are standard)
   - **Cons**: Requires additional RBAC for endpointslices resource

4. **MP-Browse** (`mp-browse/`) - 🏭 **Production Application Testing**
   - **Approach**: Your actual production application (browse-webapp) deployed in the test cluster
   - **How**: Uses the same EndpointSlice-based zone-aware load balancing as slice-client
   - **Pros**: Test your real application locally before deploying to staging/production
   - **Use Case**: Validate that zone-aware routing works with your actual app and configuration
   - **Note**: You provide your own JAR file - see `mp-browse/README.md` for setup instructions

> **Why Three Implementations (+ Production App)?**  
> We discovered that Spring Cloud's built-in `ZonePreferenceServiceInstanceListSupplier` doesn't work with Spring Cloud Kubernetes Discovery (see `SPRING_CLOUD_ISSUE.md`). These three implementations demonstrate different working approaches to achieve 100% zone-aware routing. The mp-browse integration allows you to test your actual production application in the same local environment.

#### Infrastructure
- **Kind Cluster** - Local Kubernetes cluster with nodes labeled as different zones (zone-a, zone-b)
- **RBAC** - Service accounts and roles for Kubernetes API access
- **Namespace** - All resources deployed in `lb-demo` namespace

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

You can deploy any or all of the client implementations:

**Deploy Custom Client** (Pod label queries):
```bash
./scripts/build-and-deploy.sh
```

**Deploy Simple Client** (podMetadata access - recommended):
```bash
./scripts/build-and-deploy-simple.sh
```

**Deploy Slice Client** (EndpointSlices API):
```bash
./scripts/build-and-deploy-slice.sh
```

**Deploy MP-Browse** (Your Production Application):
```bash
# First, copy your JAR file
cp /path/to/browse-webapp.jar mp-browse/app.jar

# Then deploy
./scripts/build-and-deploy-mp-browse.sh
```

Or deploy all at once:
```bash
./scripts/build-and-deploy.sh
./scripts/build-and-deploy-simple.sh
./scripts/build-and-deploy-slice.sh
# ./scripts/build-and-deploy-mp-browse.sh  # Optional - requires your JAR
```

Each script will:
- Build the service(s) with Maven
- Create Docker images
- Load images into the Kind cluster
- Deploy Kubernetes resources
- Wait for all pods to be ready

### Step 3: Test Zone-Aware Load Balancing

Run the automated test to compare all implementations:

```bash
./scripts/test-loadbalancing.sh
```

This will test **all deployed client implementations** and show:
- Results from Custom Client (if deployed)
- Results from Simple Client (if deployed)  
- Results from Slice Client (if deployed)
- Results from MP-Browse (if deployed)
- Distribution of calls across pods and zones
- Same-zone vs cross-zone call percentages

**Expected Result:** All implementations should show **100% same-zone routing**:
```json
{
  "clientZone": "zone-a",
  "totalCalls": 20,
  "sameZoneCalls": 20,
  "crossZoneCalls": 0,
  "sameZonePercentage": "100.0%"
}
```

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
├── sample-service/                    # Target service (provides /info endpoint)
│   ├── src/main/java/.../controller/
│   │   └── InfoController.java
│   ├── Dockerfile
│   └── pom.xml
│
├── client-service/                    # Custom Client (Pod label queries)
│   ├── src/main/java/.../
│   │   ├── config/
│   │   │   ├── LoadBalancerConfig.java
│   │   │   └── KubernetesClientConfig.java
│   │   ├── loadbalancer/
│   │   │   └── CustomZonePreferenceServiceInstanceListSupplier.java
│   │   └── controller/TestController.java
│   ├── Dockerfile
│   └── pom.xml
│
├── simple-client-service/             # Simple Client (podMetadata) - RECOMMENDED
│   ├── src/main/java/.../config/
│   │   ├── SimpleLoadBalancerConfig.java
│   │   └── LoggingServiceInstanceListSupplier.java  # ⭐ Key implementation
│   ├── Dockerfile
│   └── pom.xml
│
├── slice-client-service/              # Slice Client (EndpointSlices API)
│   ├── src/main/java/.../config/
│   │   ├── SliceLoadBalancerConfig.java
│   │   └── EndpointSliceZoneServiceInstanceListSupplier.java
│   ├── Dockerfile
│   └── pom.xml
│
├── mp-browse/                         # Production app integration (user provides JAR)
│   ├── Dockerfile                     # Docker config for your browse-webapp
│   ├── README.md                      # Detailed setup instructions
│   ├── .gitignore                     # Excludes app.jar from git
│   └── app.jar                        # (Not in git - you copy your JAR here)
│
├── k8s/                               # Kubernetes manifests
│   ├── namespace.yaml
│   ├── rbac.yaml                      # Includes endpointslices permissions
│   ├── sample-service.yaml
│   ├── client-service.yaml
│   ├── simple-client-service.yaml
│   ├── slice-client-service.yaml
│   └── mp-browse.yaml                 # Your production app deployment
│
├── scripts/                           # Helper scripts
│   ├── setup-kind-cluster.sh          # Create Kind cluster
│   ├── build-and-deploy.sh            # Build/deploy custom client
│   ├── build-and-deploy-simple.sh     # Build/deploy simple client
│   ├── build-and-deploy-slice.sh      # Build/deploy slice client
│   ├── build-and-deploy-mp-browse.sh  # Build/deploy your production app
│   ├── test-loadbalancing.sh          # Compare all implementations
│   ├── debug-simple-client.sh         # Remote debugging setup
│   ├── port-forward.sh
│   ├── logs.sh
│   ├── cleanup.sh
│   ├── cleanup-all.sh
│   └── destroy-cluster.sh
│
├── SPRING_CLOUD_ISSUE.md              # Ready-to-submit GitHub issue
├── FINDINGS_SUMMARY.md                # Complete investigation summary
├── ISSUE_SUBMISSION_GUIDE.md          # How to submit the issue
├── SOLUTION.md                        # Detailed solution documentation
├── DEBUG_GUIDE.md                     # Remote debugging instructions
└── pom.xml                            # Parent POM
```

### Key Implementation Files

Each client demonstrates a different approach to accessing zone information:

1. **`client-service/CustomZonePreferenceServiceInstanceListSupplier.java`**
   - Queries Kubernetes API for pod details by IP
   - Extracts zone from pod labels

2. **`simple-client-service/LoggingServiceInstanceListSupplier.java`** ⭐ **Recommended**
   - Accesses `DefaultKubernetesServiceInstance.podMetadata()` directly
   - Reads zone from the pod labels structure

3. **`slice-client-service/EndpointSliceZoneServiceInstanceListSupplier.java`**
   - Uses Kubernetes EndpointSlices API
   - Builds IP-to-zone cache from `endpoint.getZone()`

## 🧹 Cleanup

Three cleanup options available:

### Quick Cleanup (No Confirmation)
```bash
./scripts/cleanup.sh
```
Immediately deletes the Kind cluster.

### Safe Cleanup (With Confirmation)
```bash
./scripts/destroy-cluster.sh
```
Asks for confirmation before deleting the cluster.

### Complete Cleanup (Cluster + Docker Images)
```bash
./scripts/cleanup-all.sh
```
Deletes cluster and removes all Docker images for this project.

**See** `CLEANUP_GUIDE.md` for detailed information on each cleanup option.

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

