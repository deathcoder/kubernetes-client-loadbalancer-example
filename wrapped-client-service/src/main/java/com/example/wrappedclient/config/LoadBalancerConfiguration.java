package com.example.wrappedclient.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.cloud.kubernetes.client.discovery.KubernetesInformerDiscoveryClient;
import org.springframework.cloud.kubernetes.commons.discovery.DefaultKubernetesServiceInstance;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClients;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LoadBalancer configuration using DiscoveryClient wrapper approach.
 * 
 * This approach wraps the KubernetesInformerDiscoveryClient to extract zone information
 * from podMetadata and add it to the ServiceInstance metadata where Spring Cloud's
 * built-in ZonePreferenceServiceInstanceListSupplier expects to find it.
 * 
 * This allows us to use Spring Cloud's native .withZonePreference() instead of
 * implementing custom filtering logic.
 */
@Configuration
@LoadBalancerClients(defaultConfiguration = LoadBalancerConfiguration.ZoneAwareLoadBalancerConfig.class)
public class LoadBalancerConfiguration {

    private static final Logger log = LoggerFactory.getLogger(LoadBalancerConfiguration.class);

    private final String currentZone;

    public LoadBalancerConfiguration(@Value("${spring.cloud.loadbalancer.zone:unknown}") String currentZone) {
        this.currentZone = currentZone;
        log.info("Initialized LoadBalancerConfiguration with zone: {}", currentZone);
    }

  
    @Bean
    DiscoveryClient discoveryClient(KubernetesInformerDiscoveryClient client) {
        return new DiscoveryClient() {
            @Override
            public String description() {
                return client.description();
            }

            @Override
            public List<ServiceInstance> getInstances(String serviceId) {
                List<ServiceInstance> result = client.getInstances(serviceId)
                        .stream()
                        .map(service -> {
                            if (service instanceof DefaultKubernetesServiceInstance) {
                                DefaultKubernetesServiceInstance s = (DefaultKubernetesServiceInstance) service;
                                String zone = s.podMetadata().getOrDefault("labels", Map.of()).get("topology.kubernetes.io/zone");
                                return new ServiceInstance() {
                                    @Override
                                    public String getServiceId() {
                                        return s.getServiceId();
                                    }

                                    @Override
                                    public String getHost() {
                                        return s.getHost();
                                    }

                                    @Override
                                    public int getPort() {
                                        return s.getPort();
                                    }

                                    @Override
                                    public boolean isSecure() {
                                        return s.isSecure();
                                    }

                                    @Override
                                    public URI getUri() {
                                        return s.getUri();
                                    }

                                    @Override
                                    public Map<String, String> getMetadata() {
                                        if (zone != null) {
                                            Map<String, String> result = new HashMap<>(s.getMetadata());
                                            result.putIfAbsent("zone", zone);
                                            return result;
                                        }
                                        return s.getMetadata();
                                    }
                                };
                            }
                            return service;
                        })
                        .toList();
                return result;
            }

            @Override
            public List<String> getServices() {
                return client.getServices();
            }
        };
    }
    /**
     * LoadBalancer configuration using Spring Cloud's built-in zone preference.
     * This works because our wrapped DiscoveryClient exposes zone in metadata.
     */
    public static class ZoneAwareLoadBalancerConfig {
        
        private static final Logger log = LoggerFactory.getLogger(ZoneAwareLoadBalancerConfig.class);
        
        @Bean
        public ServiceInstanceListSupplier discoveryClientServiceInstanceListSupplier(
                ConfigurableApplicationContext context) {
            
            log.info("Creating ServiceInstanceListSupplier with built-in zone preference");
            
            // Use Spring Cloud's built-in zone preference!
            // This works because our BeanPostProcessor exposes zone in metadata
            return ServiceInstanceListSupplier.builder()
                    .withBlockingDiscoveryClient()
                    .withCaching()
                    .withZonePreference()  // Built-in zone preference now works!
                    .build(context);
        }
    }
}

