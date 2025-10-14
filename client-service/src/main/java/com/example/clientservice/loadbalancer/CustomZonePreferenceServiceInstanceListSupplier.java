package com.example.clientservice.loadbalancer;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Custom ServiceInstanceListSupplier that filters instances to prefer those in the same zone.
 * Falls back to all instances if no same-zone instances are available.
 * Fetches zone information directly from Kubernetes pods.
 */
public class CustomZonePreferenceServiceInstanceListSupplier implements ServiceInstanceListSupplier {

    private static final Logger log = LoggerFactory.getLogger(CustomZonePreferenceServiceInstanceListSupplier.class);

    private final ServiceInstanceListSupplier delegate;
    private final String clientZone;
    private final KubernetesClient kubernetesClient;

    public CustomZonePreferenceServiceInstanceListSupplier(ServiceInstanceListSupplier delegate, String clientZone, KubernetesClient kubernetesClient) {
        this.delegate = delegate;
        this.clientZone = clientZone;
        this.kubernetesClient = kubernetesClient;
        log.info("Initialized CustomZonePreferenceServiceInstanceListSupplier with client zone: {}", clientZone);
    }

    @Override
    public String getServiceId() {
        return delegate.getServiceId();
    }

    @Override
    public Flux<List<ServiceInstance>> get() {
        return delegate.get().map(this::filterByZone);
    }

    private List<ServiceInstance> filterByZone(List<ServiceInstance> instances) {
        if (clientZone == null || clientZone.isEmpty() || "unknown".equals(clientZone)) {
            log.debug("Client zone not configured, returning all {} instances", instances.size());
            return instances;
        }

        // Try to filter instances in the same zone
        List<ServiceInstance> sameZoneInstances = instances.stream()
                .filter(instance -> {
                    String instanceZone = getInstanceZone(instance);
                    boolean match = clientZone.equals(instanceZone);
                    log.debug("Instance {} zone: {}, client zone: {}, match: {}", 
                            instance.getInstanceId(), instanceZone, clientZone, match);
                    return match;
                })
                .collect(Collectors.toList());

        if (!sameZoneInstances.isEmpty()) {
            log.info("Filtered {} instances to {} same-zone instances (zone: {})", 
                    instances.size(), sameZoneInstances.size(), clientZone);
            return sameZoneInstances;
        }

        log.warn("No instances found in zone {}, falling back to all {} instances", 
                clientZone, instances.size());
        return instances;
    }

    private String getInstanceZone(ServiceInstance instance) {
        // First try metadata
        String zone = instance.getMetadata().get("zone");
        if (zone != null) {
            log.debug("Found zone at root level: {}", zone);
            return zone;
        }

        zone = instance.getMetadata().get("topology.kubernetes.io/zone");
        if (zone != null) {
            log.debug("Found zone at topology.kubernetes.io/zone: {}", zone);
            return zone;
        }

        // Check if labels are nested as a Map
        Object labelsObj = instance.getMetadata().get("labels");
        if (labelsObj != null && labelsObj instanceof java.util.Map) {
            @SuppressWarnings("unchecked")
            java.util.Map<String, String> labels = (java.util.Map<String, String>) labelsObj;
            zone = labels.get("zone");
            if (zone != null) {
                log.debug("Found zone in labels map: {}", zone);
                return zone;
            }
            zone = labels.get("topology.kubernetes.io/zone");
            if (zone != null) {
                log.debug("Found zone in labels map (topology key): {}", zone);
                return zone;
            }
        }

        // Last resort: fetch from Kubernetes API
        zone = fetchZoneFromKubernetes(instance);
        if (zone != null) {
            return zone;
        }

        log.warn("No zone found for instance {}. Metadata keys: {}", 
                instance.getInstanceId(), instance.getMetadata().keySet());
        return null;
    }

    private String fetchZoneFromKubernetes(ServiceInstance instance) {
        try {
            log.debug("Attempting to fetch zone from Kubernetes for instance: {}", instance.getInstanceId());
            
            // Check if KubernetesClient is available
            if (kubernetesClient == null) {
                log.warn("KubernetesClient is null, cannot fetch zone from Kubernetes API");
                return null;
            }
            
            // Get pod IP and namespace
            String podIp = instance.getHost();
            String namespace = instance.getMetadata().getOrDefault("k8s_namespace", "lb-demo");
            
            log.debug("Searching for pod with IP: {} in namespace: {}", podIp, namespace);
            
            if (podIp == null || podIp.isEmpty()) {
                log.warn("Could not get pod IP from instance {}", instance.getInstanceId());
                return null;
            }

            // Query all pods in the namespace and find by IP
            log.debug("Querying Kubernetes API for pods in namespace: {}", namespace);
            java.util.List<Pod> pods = kubernetesClient.pods()
                    .inNamespace(namespace)
                    .list()
                    .getItems();

            Pod matchingPod = null;
            for (Pod pod : pods) {
                if (pod.getStatus() != null && podIp.equals(pod.getStatus().getPodIP())) {
                    matchingPod = pod;
                    break;
                }
            }

            if (matchingPod == null) {
                log.warn("Pod with IP {} not found in namespace {}", podIp, namespace);
                return null;
            }

            log.debug("Found pod: {} with IP: {}", matchingPod.getMetadata().getName(), podIp);

            if (matchingPod.getMetadata() != null && matchingPod.getMetadata().getLabels() != null) {
                java.util.Map<String, String> labels = matchingPod.getMetadata().getLabels();
                log.debug("Pod labels: {}", labels);
                
                String zone = labels.get("zone");
                if (zone == null) {
                    zone = labels.get("topology.kubernetes.io/zone");
                }
                if (zone != null) {
                    log.info("Successfully fetched zone from Kubernetes for pod {} (IP: {}): {}", 
                            matchingPod.getMetadata().getName(), podIp, zone);
                    return zone;
                } else {
                    log.warn("Pod {} has labels but no zone label found. Labels: {}", 
                            matchingPod.getMetadata().getName(), labels);
                }
            } else {
                log.warn("Pod {} has no labels", matchingPod.getMetadata().getName());
            }
        } catch (Exception e) {
            log.error("Failed to fetch zone from Kubernetes for instance {}: {}", 
                    instance.getInstanceId(), e.getMessage(), e);
        }
        return null;
    }

    private String extractPodName(ServiceInstance instance) {
        String host = instance.getHost();
        String instanceId = instance.getInstanceId();
        
        log.debug("Extracting pod name - Host: {}, InstanceId: {}", host, instanceId);
        
        // Try hostname first (pod-name.service.namespace.svc.cluster.local)
        if (host != null && host.contains(".")) {
            String podName = host.split("\\.")[0];
            log.debug("Extracted pod name from host: {}", podName);
            return podName;
        }
        
        // Try instance ID (might be the pod name directly)
        if (instanceId != null && !instanceId.contains(":") && !instanceId.contains("/")) {
            log.debug("Using instance ID as pod name: {}", instanceId);
            return instanceId;
        }
        
        // Last resort: use host as-is
        log.debug("Using host as pod name: {}", host);
        return host;
    }
}

