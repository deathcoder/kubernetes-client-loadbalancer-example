package com.example.simpleclient.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.kubernetes.commons.discovery.DefaultKubernetesServiceInstance;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Logging wrapper to inspect what metadata Spring Cloud Kubernetes provides
 */
public class LoggingServiceInstanceListSupplier implements ServiceInstanceListSupplier {

    private static final Logger log = LoggerFactory.getLogger(LoggingServiceInstanceListSupplier.class);

    private final ServiceInstanceListSupplier delegate;
    private final String clientZone;

    public LoggingServiceInstanceListSupplier(ServiceInstanceListSupplier delegate, String clientZone) {
        this.delegate = delegate;
        this.clientZone = clientZone;
        log.info("=".repeat(80));
        log.info("LoggingServiceInstanceListSupplier initialized");
        log.info("Client Zone: {}", clientZone);
        log.info("Delegate: {}", delegate.getClass().getName());
        log.info("=".repeat(80));
    }

    @Override
    public String getServiceId() {
        return delegate.getServiceId();
    }

    @Override
    public Flux<List<ServiceInstance>> get() {
        return delegate.get().map(instances -> {
            log.info("=" .repeat(80));
            log.info("SERVICE INSTANCE LIST RECEIVED (BEFORE FILTERING)");
            log.info("Client Zone: {}", clientZone);
            log.info("Total Instances: {}", instances.size());
            log.info("-".repeat(80));
            
            for (int i = 0; i < instances.size(); i++) {
                ServiceInstance instance = instances.get(i);
                log.info("Instance #{}: {}", i + 1, instance.getInstanceId());
                log.info("  Instance Class: {}", instance.getClass().getName());
                log.info("  Host: {}", instance.getHost());
                log.info("  Port: {}", instance.getPort());
                log.info("  URI: {}", instance.getUri());
                log.info("  Scheme: {}", instance.getScheme());
                log.info("  Service ID: {}", instance.getServiceId());
                
                // Try to see if there are additional methods on KubernetesServiceInstance
                try {
                    Class<?> clazz = instance.getClass();
                    log.info("  Available methods on instance:");
                    java.lang.reflect.Method[] methods = clazz.getMethods();
                    for (java.lang.reflect.Method method : methods) {
                        String methodName = method.getName();
                        if (methodName.toLowerCase().contains("zone") || 
                            methodName.toLowerCase().contains("pod") ||
                            methodName.toLowerCase().contains("label") ||
                            methodName.toLowerCase().contains("metadata")) {
                            log.info("    - {}", method.toString());
                        }
                    }
                } catch (Exception e) {
                    log.debug("Could not inspect instance methods: {}", e.getMessage());
                }
                
                // Log all metadata
                Map<String, String> metadata = instance.getMetadata();
                log.info("  Metadata Keys: {}", metadata.keySet());
                log.info("  Metadata Class: {}", metadata.getClass().getName());
                
                // Check for nested structures
                for (Map.Entry<String, String> entry : metadata.entrySet()) {
                    String key = entry.getKey();
                    String value = entry.getValue();
                    
                    // Check if this might be a nested structure
                    if (key.contains("pod") || key.contains("label") || key.contains("annotation")) {
                        log.info("  POTENTIAL NESTED STRUCTURE: key={}", key);
                    }
                    
                    // Truncate very long values
                    if (value != null && value.length() > 200) {
                        value = value.substring(0, 200) + "... [truncated]";
                    }
                    
                    log.info("    {}: {}", key, value);
                }
                
                // Try to access as generic object to see if there's hidden data
                try {
                    Object rawMetadata = instance.getMetadata();
                    if (rawMetadata instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> objectMap = (Map<String, Object>) rawMetadata;
                        for (Map.Entry<String, Object> entry : objectMap.entrySet()) {
                            Object value = entry.getValue();
                            if (value instanceof Map) {
                                log.info("  FOUND NESTED MAP at key '{}': {}", entry.getKey(), value);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.debug("Could not inspect raw metadata: {}", e.getMessage());
                }
                
                // Try to call podMetadata() if available
                Map<String, Map<String, String>> podMetadata = null;
                try {
                    if (instance instanceof org.springframework.cloud.kubernetes.commons.discovery.DefaultKubernetesServiceInstance) {
                        org.springframework.cloud.kubernetes.commons.discovery.DefaultKubernetesServiceInstance k8sInstance = 
                            (org.springframework.cloud.kubernetes.commons.discovery.DefaultKubernetesServiceInstance) instance;
                        podMetadata = k8sInstance.podMetadata();
                        log.info("  *** POD METADATA FOUND! ***");
                        log.info("  Pod Metadata Keys (top level): {}", podMetadata.keySet());
                        for (Map.Entry<String, Map<String, String>> entry : podMetadata.entrySet()) {
                            log.info("    podMetadata.{} = {}", entry.getKey(), entry.getValue());
                            if (entry.getValue() != null) {
                                for (Map.Entry<String, String> subEntry : entry.getValue().entrySet()) {
                                    log.info("      {}: {}", subEntry.getKey(), subEntry.getValue());
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("Could not access podMetadata(): {}", e.getMessage());
                }
                
                // Check for zone information in various places
                String zoneFromMetadata = metadata.get("zone");
                String zoneFromTopology = metadata.get("topology.kubernetes.io/zone");
                String zoneFromLabels = checkNestedLabels(metadata);
                String zoneFromPodMetadata = null;
                
                if (podMetadata != null) {
                    // podMetadata is Map<String, Map<String, String>>
                    // Structure: {labels={zone=zone-a, ...}, annotations={...}}
                    Map<String, String> labels = podMetadata.get("labels");
                    if (labels != null) {
                        zoneFromPodMetadata = labels.get("zone");
                        if (zoneFromPodMetadata == null) {
                            zoneFromPodMetadata = labels.get("topology.kubernetes.io/zone");
                        }
                    }
                }
                
                log.info("  Zone Detection:");
                log.info("    - metadata.get('zone'): {}", zoneFromMetadata);
                log.info("    - metadata.get('topology.kubernetes.io/zone'): {}", zoneFromTopology);
                log.info("    - nested labels check: {}", zoneFromLabels);
                log.info("    - podMetadata() zone: {}", zoneFromPodMetadata);
                
                if (zoneFromMetadata != null || zoneFromTopology != null || zoneFromLabels != null || zoneFromPodMetadata != null) {
                    log.info("  ✓ ZONE FOUND!");
                } else {
                    log.warn("  ✗ NO ZONE INFORMATION FOUND");
                }
                
                log.info("-".repeat(80));
            }
            
            log.info("=".repeat(80));
            
            // Now filter by zone using podMetadata()
            if (clientZone == null || clientZone.isEmpty() || "unknown".equalsIgnoreCase(clientZone)) {
                log.info("Client zone is unknown, returning all {} instances (no filtering)", instances.size());
                return instances;
            }
            
            log.info("FILTERING INSTANCES BY ZONE: {}", clientZone);
            List<ServiceInstance> sameZoneInstances = instances.stream()
                    .filter(instance -> {
                        String instanceZone = getInstanceZoneFromPodMetadata(instance);
                        boolean matches = clientZone.equalsIgnoreCase(instanceZone);
                        log.info("  Instance {} - Zone: {} - Matches: {}", 
                                instance.getInstanceId(), instanceZone, matches);
                        return matches;
                    })
                    .collect(Collectors.toList());
            
            if (!sameZoneInstances.isEmpty()) {
                log.info("✓ Filtered to {} instances in zone {}", sameZoneInstances.size(), clientZone);
                return sameZoneInstances;
            } else {
                log.warn("✗ No instances found in zone {}, falling back to all {} instances", 
                        clientZone, instances.size());
                return instances;
            }
        });
    }
    
    /**
     * Extract zone from podMetadata() which is where Spring Cloud Kubernetes stores pod labels
     */
    private String getInstanceZoneFromPodMetadata(ServiceInstance instance) {
        if (!(instance instanceof DefaultKubernetesServiceInstance)) {
            return null;
        }
        
        try {
            DefaultKubernetesServiceInstance k8sInstance = (DefaultKubernetesServiceInstance) instance;
            Map<String, Map<String, String>> podMetadata = k8sInstance.podMetadata();
            
            if (podMetadata != null && podMetadata.containsKey("labels")) {
                Map<String, String> labels = podMetadata.get("labels");
                String zone = labels.get("zone");
                if (zone == null) {
                    zone = labels.get("topology.kubernetes.io/zone");
                }
                return zone;
            }
        } catch (Exception e) {
            log.debug("Failed to get zone from podMetadata for {}: {}", 
                    instance.getInstanceId(), e.getMessage());
        }
        
        return null;
    }

    private String checkNestedLabels(Map<String, String> metadata) {
        // Check if labels are nested as a map
        Object labelsObj = metadata.get("labels");
        if (labelsObj != null) {
            if (labelsObj instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, String> labels = (Map<String, String>) labelsObj;
                String zone = labels.get("zone");
                if (zone == null) {
                    zone = labels.get("topology.kubernetes.io/zone");
                }
                return zone;
            } else {
                log.info("    - labels object type: {}", labelsObj.getClass().getName());
            }
        }
        return null;
    }
}

