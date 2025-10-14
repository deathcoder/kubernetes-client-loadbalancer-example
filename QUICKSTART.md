# Quick Start Guide

Get up and running in 3 commands:

## 1️⃣ Setup Cluster (2 minutes)

```bash
./scripts/setup-kind-cluster.sh
```

This creates a local Kubernetes cluster with simulated availability zones.

## 2️⃣ Build & Deploy (3-4 minutes)

```bash
./scripts/build-and-deploy.sh
```

This builds your applications, creates Docker images, and deploys everything to Kubernetes.

## 3️⃣ Test It! (30 seconds)

```bash
./scripts/test-loadbalancing.sh
```

This runs automated tests showing that traffic stays within the same zone.

---

## 📊 What You'll See

**Expected output from test:**

```json
{
  "clientZone": "zone-a",
  "totalCalls": 20,
  "sameZoneCalls": 20,
  "crossZoneCalls": 0,
  "sameZonePercentage": "100.0%",
  "podDistribution": {
    "sample-service-zone-a-xxx-1": 10,
    "sample-service-zone-a-xxx-2": 10
  },
  "zoneDistribution": {
    "zone-a": 20
  }
}
```

✅ **100% same-zone calls** means zone-aware load balancing is working!

---

## 🔄 Fast Development Loop

Made changes to your code? Rebuild and redeploy in ~30 seconds:

```bash
# Rebuild just the client service
./scripts/dev-rebuild.sh client-service

# Rebuild just the sample service
./scripts/dev-rebuild.sh sample-service

# Rebuild everything
./scripts/dev-rebuild.sh all
```

**Note:** The `build-and-deploy.sh` script automatically restarts deployments to ensure new images are loaded. If you see "unchanged" messages, the script will force a restart after applying the manifests.

---

## 🌐 Access via Browser

```bash
# Port forward to client service
./scripts/port-forward.sh client-service zone-a
```

Then visit in your browser:
- http://localhost:8081/client-info
- http://localhost:8081/test-loadbalancing?calls=50

---

## 🧹 When Done

Clean up everything:

```bash
./scripts/cleanup.sh
```

---

## 💡 Key Files to Modify

- **`client-service/src/main/java/com/example/clientservice/controller/TestController.java`** - Add your client logic here
- **`client-service/src/main/resources/application.yml`** - Configure load balancing behavior
- **`sample-service/src/main/java/com/example/sampleservice/controller/InfoController.java`** - Add your service logic here

---

## 🆘 Need Help?

See the full [README.md](README.md) for:
- Architecture details
- Troubleshooting guide
- Manual testing options
- How it all works

---

**That's it!** You now have a local test environment that's 10x faster than deploying to a real cluster. 🚀

