# Remote Debugging Guide

## Quick Start

1. **Build and deploy with debugging enabled:**
   ```bash
   ./scripts/build-and-deploy-simple.sh
   ```

2. **Set up port forwarding:**
   ```bash
   # For zone-a pod
   ./scripts/debug-simple-client.sh zone-a
   
   # OR for zone-b pod
   ./scripts/debug-simple-client.sh zone-b
   ```

3. **Connect your IDE** (see configuration below)

4. **Set breakpoints** in:
   - `LoggingServiceInstanceListSupplier.java` - to see metadata inspection
   - `SimpleLoadBalancerConfig.java` - to see load balancer initialization
   - `TestController.java` - to see request handling

5. **Trigger a request:**
   ```bash
   curl "http://localhost:8081/test-loadbalancing?calls=4"
   ```

---

## IDE Configuration

### IntelliJ IDEA

1. **Go to:** Run → Edit Configurations
2. **Click:** + → Remote JVM Debug
3. **Configure:**
   - Name: `Debug Simple Client`
   - Host: `localhost`
   - Port: `5005`
   - Debugger mode: `Attach to remote JVM`
   - Command line arguments: `-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005`

4. **Click:** Apply → OK
5. **Start debugging:** Run → Debug 'Debug Simple Client'

### VS Code

1. **Add to `.vscode/launch.json`:**
   ```json
   {
     "type": "java",
     "name": "Debug Simple Client",
     "request": "attach",
     "hostName": "localhost",
     "port": 5005
   }
   ```

2. **Start debugging:** Run → Start Debugging (F5)

### Eclipse

1. **Go to:** Run → Debug Configurations
2. **Right-click:** Remote Java Application → New
3. **Configure:**
   - Project: `simple-client-service`
   - Connection Type: `Standard (Socket Attach)`
   - Host: `localhost`
   - Port: `5005`

4. **Click:** Debug

---

## Debugging Scenarios

### Scenario 1: Inspect Service Instance Metadata

**Goal:** See what metadata Spring Cloud Kubernetes provides

**Breakpoints:**
- `LoggingServiceInstanceListSupplier.java:48` - When instances are received
- Line with `instance.getMetadata()` - To inspect metadata map
- Line with `instance.podMetadata()` - To inspect pod metadata

**Test:**
```bash
curl "http://localhost:8081/test-loadbalancing?calls=2"
```

**What to inspect:**
- `instances` list - all discovered service instances
- `instance.getMetadata()` - service-level metadata (missing zone!)
- `podMetadata` - pod-level metadata with labels and annotations
- `podMetadata.get("labels")` - should contain zone labels

### Scenario 2: Trace Zone Preference Logic

**Goal:** See how Spring Cloud LoadBalancer's built-in zone preference works

**Breakpoints:**
- `SimpleLoadBalancerConfig.java:30` - Load balancer initialization
- Any Spring Cloud LoadBalancer classes (if source is available)

**Test:**
```bash
curl "http://localhost:8081/test-loadbalancing?calls=10"
```

### Scenario 3: Compare with Custom Implementation

**Goal:** Understand why custom implementation works but built-in doesn't

**Steps:**
1. Debug simple-client-service (built-in zone preference)
2. Note the metadata structure and zone detection
3. Debug client-service (custom implementation)
4. Compare the approaches

---

## Useful Debugging Commands

### Check if debug port is accessible
```bash
# From your local machine
nc -zv localhost 5005
```

### View logs while debugging
```bash
# In another terminal
kubectl logs -n lb-demo -l app=simple-client-service,zone=zone-a -f
```

### List all pods with debug ports
```bash
kubectl get pods -n lb-demo -l app=simple-client-service -o wide
```

### Check service endpoints
```bash
kubectl get endpoints -n lb-demo simple-client-service
```

### Restart pod if needed
```bash
kubectl rollout restart deployment/simple-client-service-zone-a -n lb-demo
```

---

## Troubleshooting

### Can't connect to debugger

**Problem:** Connection refused on port 5005

**Solutions:**
1. Check port forwarding is running:
   ```bash
   ps aux | grep "kubectl port-forward"
   ```

2. Verify pod is running:
   ```bash
   kubectl get pods -n lb-demo -l app=simple-client-service
   ```

3. Check debug port in pod:
   ```bash
   POD=$(kubectl get pod -n lb-demo -l app=simple-client-service,zone=zone-a -o jsonpath='{.items[0].metadata.name}')
   kubectl logs -n lb-demo $POD | grep "Listening for transport"
   ```

### Breakpoints not hitting

**Problem:** Debugger connected but breakpoints aren't triggered

**Solutions:**
1. Verify source code matches deployed version:
   ```bash
   # Check last build time
   ls -lh simple-client-service/target/*.jar
   ```

2. Rebuild and redeploy:
   ```bash
   ./scripts/build-and-deploy-simple.sh
   ```

3. Trigger a request:
   ```bash
   curl "http://localhost:8081/test-loadbalancing?calls=1"
   ```

### Pod keeps restarting

**Problem:** Pod enters CrashLoopBackOff

**Solutions:**
1. Check logs:
   ```bash
   kubectl logs -n lb-demo <pod-name> --previous
   ```

2. Try with suspend=y to wait for debugger:
   ```yaml
   # In Dockerfile
   ENTRYPOINT ["java", "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005", "-jar", "app.jar"]
   ```
   **Note:** With `suspend=y`, the application waits for debugger to connect before starting

---

## Advanced Debugging

### Debug with suspend=y (wait for debugger)

Useful when you need to debug startup code:

1. **Update Dockerfile:**
   ```dockerfile
   ENTRYPOINT ["java", "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005", "-jar", "app.jar"]
   ```

2. **Rebuild and deploy:**
   ```bash
   ./scripts/build-and-deploy-simple.sh
   ```

3. **Quickly set up port forwarding and connect debugger:**
   ```bash
   # Pod will be waiting for debugger!
   ./scripts/debug-simple-client.sh zone-a
   ```

### Debug multiple instances

You can debug multiple pods on different local ports:

**Terminal 1 (zone-a):**
```bash
POD_A=$(kubectl get pod -n lb-demo -l app=simple-client-service,zone=zone-a -o jsonpath='{.items[0].metadata.name}')
kubectl port-forward -n lb-demo $POD_A 8081:8081 5005:5005
```

**Terminal 2 (zone-b):**
```bash
POD_B=$(kubectl get pod -n lb-demo -l app=simple-client-service,zone=zone-b -o jsonpath='{.items[0].metadata.name}')
kubectl port-forward -n lb-demo $POD_B 8082:8081 5006:5005
```

Configure two debug configurations in your IDE (ports 5005 and 5006).

---

## Key Files to Debug

| File | Purpose | Key Methods |
|------|---------|-------------|
| `LoggingServiceInstanceListSupplier.java` | Inspects metadata | `get()`, `checkNestedLabels()` |
| `SimpleLoadBalancerConfig.java` | Configures load balancer | `serviceInstanceListSupplier()` |
| `TestController.java` | Handles test requests | `testLoadBalancing()` |

---

## Example Debug Session

```
1. Start port forwarding:
   ./scripts/debug-simple-client.sh zone-a

2. Connect IDE debugger to localhost:5005

3. Set breakpoint at LoggingServiceInstanceListSupplier:120
   (line where podMetadata is accessed)

4. Trigger request:
   curl "http://localhost:8081/test-loadbalancing?calls=1"

5. When breakpoint hits, inspect:
   - instance (type: DefaultKubernetesServiceInstance)
   - podMetadata (Map<String, Map<String, String>>)
   - podMetadata.get("labels") (should have zone labels)
   - podMetadata.get("labels").get("zone") (should be "zone-a")

6. Step through zone detection logic

7. Compare with metadata from instance.getMetadata()
   (should NOT have zone labels)
```

This proves why our custom solution is needed!

