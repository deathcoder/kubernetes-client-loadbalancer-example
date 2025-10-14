package com.example.sliceclient.config;

import io.fabric8.kubernetes.api.model.discovery.v1.Endpoint;
import io.fabric8.kubernetes.api.model.discovery.v1.EndpointSlice;
import io.fabric8.kubernetes.api.model.discovery.v1.EndpointSliceList;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Custom ServiceInstanceListSupplier that uses EndpointSlices to determine zone information.
 * This approach uses the standard Kubernetes topology information from EndpointSlices
 * instead of relying on custom pod labels.
 */
public class EndpointSliceZoneServiceInstanceListSupplier implements ServiceInstanceListSupplier {

    private static final Logger log = LoggerFactory.getLogger(EndpointSliceZoneServiceInstanceListSupplier.class);

    private final ServiceInstanceListSupplier delegate;
    private final String clientZone;
    private final KubernetesClient kubernetesClient;
    private final String namespace;

    // Cache for IP to zone mapping
    private final Map<String, String> ipToZoneCache = new HashMap<>();

    public EndpointSliceZoneServiceInstanceListSupplier(
            ServiceInstanceListSupplier delegate,
            String clientZone,
            KubernetesClient kubernetesClient,
            String namespace) {
        this.delegate = delegate;
        this.clientZone = clientZone;
        this.kubernetesClient = kubernetesClient;
        this.namespace = namespace;
        log.info("Initialized EndpointSliceZoneServiceInstanceListSupplier with client zone: {}", clientZone);
    }

    @Override
    public String getServiceId() {
        return delegate.getServiceId();
    }

    @Override
    public Flux<List<ServiceInstance>> get() {
        // Refresh the zone cache from EndpointSlices
        refreshZoneCache();

        return delegate.get().map(instances -> {
            if (clientZone == null || clientZone.isEmpty() || "unknown".equalsIgnoreCase(clientZone)) {
                log.debug("Client zone is unknown or not set, returning all {} instances.", instances.size());
                return instances;
            }

            log.info("=".repeat(80));
            log.info("FILTERING INSTANCES USING ENDPOINTSLICE ZONE INFORMATION");
            log.info("Client Zone: {}", clientZone);
            log.info("Total Instances: {}", instances.size());
            log.info("-".repeat(80));

            List<ServiceInstance> sameZoneInstances = instances.stream()
                    .filter(instance -> {
                        String instanceZone = getInstanceZoneFromEndpointSlice(instance);
                        boolean sameZone = clientZone.equalsIgnoreCase(instanceZone);
                        log.info("Instance: {} ({}:{}) - Zone: {} - Same Zone: {}",
                                instance.getInstanceId(),
                                instance.getHost(),
                                instance.getPort(),
                                instanceZone,
                                sameZone);
                        return sameZone;
                    })
                    .collect(Collectors.toList());

            if (!sameZoneInstances.isEmpty()) {
                log.info("Found {} instances in the same zone ({}) as the client.", sameZoneInstances.size(), clientZone);
                log.info("=".repeat(80));
                return sameZoneInstances;
            } else {
                log.warn("No instances found in zone {}, falling back to all {} instances",
                        clientZone, instances.size());
                log.info("=".repeat(80));
                return instances;
            }
        });
    }

    /**
     * Refresh the IP to zone mapping cache by querying EndpointSlices.
     */
    private void refreshZoneCache() {
        try {
            if (kubernetesClient == null) {
                log.warn("KubernetesClient is null, cannot refresh zone cache from EndpointSlices");
                return;
            }

            log.debug("Querying EndpointSlices for service: {} in namespace: {}", getServiceId(), namespace);

            // Query EndpointSlices for the service
            EndpointSliceList endpointSliceList = kubernetesClient.discovery().v1()
                    .endpointSlices()
                    .inNamespace(namespace)
                    .withLabel("kubernetes.io/service-name", getServiceId())
                    .list();

            log.info("Found {} EndpointSlices for service {}", endpointSliceList.getItems().size(), getServiceId());

            ipToZoneCache.clear();

            for (EndpointSlice slice : endpointSliceList.getItems()) {
                log.debug("Processing EndpointSlice: {}", slice.getMetadata().getName());

                if (slice.getEndpoints() != null) {
                    for (Endpoint endpoint : slice.getEndpoints()) {
                        // Get the zone from endpoint topology
                        String zone = null;
                        if (endpoint.getZone() != null) {
                            zone = endpoint.getZone();
                        }

                        // Get IP addresses
                        if (endpoint.getAddresses() != null) {
                            for (String ip : endpoint.getAddresses()) {
                                if (zone != null) {
                                    ipToZoneCache.put(ip, zone);
                                    log.debug("Mapped IP {} to zone {} from EndpointSlice", ip, zone);
                                } else {
                                    log.warn("Endpoint with IP {} has no zone information in EndpointSlice", ip);
                                }
                            }
                        }
                    }
                }
            }

            log.info("Zone cache refreshed. Total IP to zone mappings: {}", ipToZoneCache.size());
            log.debug("IP to Zone mappings: {}", ipToZoneCache);

        } catch (Exception e) {
            log.error("Failed to refresh zone cache from EndpointSlices: {}", e.getMessage(), e);
        }
    }

    /**
     * Get the zone for a service instance from the EndpointSlice cache.
     */
    private String getInstanceZoneFromEndpointSlice(ServiceInstance instance) {
        String ip = instance.getHost();
        String zone = ipToZoneCache.get(ip);

        if (zone == null) {
            log.warn("No zone found in EndpointSlice cache for IP: {}. Cache contents: {}",
                    ip, ipToZoneCache);
        }

        return zone;
    }
}

