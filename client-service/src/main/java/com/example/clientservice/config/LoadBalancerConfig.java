package com.example.clientservice.config;

import com.example.clientservice.loadbalancer.CustomZonePreferenceServiceInstanceListSupplier;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for zone-aware load balancing.
 * Uses a custom supplier that properly filters instances by zone.
 */
@Configuration
@LoadBalancerClient(name = "sample-service", configuration = LoadBalancerConfig.SampleServiceLoadBalancerConfig.class)
public class LoadBalancerConfig {

    /**
     * Configuration specific to sample-service with zone-aware routing
     */
    public static class SampleServiceLoadBalancerConfig {

        @Value("${spring.cloud.loadbalancer.zone:unknown}")
        private String zone;

        @Bean
        public ServiceInstanceListSupplier serviceInstanceListSupplier(
                ConfigurableApplicationContext context) {
            
            // Build the base supplier with discovery client
            ServiceInstanceListSupplier delegate = 
                ServiceInstanceListSupplier.builder()
                    .withDiscoveryClient()
                    .build(context);
            
            // Try to get KubernetesClient from parent context (optional)
            KubernetesClient kubernetesClient = null;
            try {
                // First try the current context
                kubernetesClient = context.getBean(KubernetesClient.class);
            } catch (Exception e) {
                // Try parent context
                try {
                    if (context.getParent() != null) {
                        kubernetesClient = context.getParent().getBean(KubernetesClient.class);
                    }
                } catch (Exception ex) {
                    // KubernetesClient not available in parent either
                }
            }
            
            // Wrap with our custom zone-aware supplier that can fetch zone info from Kubernetes
            return new CustomZonePreferenceServiceInstanceListSupplier(delegate, zone, kubernetesClient);
        }
    }
}

