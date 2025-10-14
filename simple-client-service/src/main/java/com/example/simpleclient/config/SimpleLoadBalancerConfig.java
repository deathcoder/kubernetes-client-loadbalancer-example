package com.example.simpleclient.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Simple configuration using Spring Cloud's built-in zone preference
 * with logging wrapper to inspect the metadata
 */
@Configuration
@LoadBalancerClient(
    name = "sample-service",
    configuration = SimpleLoadBalancerConfig.SampleServiceLoadBalancerConfig.class
)
public class SimpleLoadBalancerConfig {

    public static class SampleServiceLoadBalancerConfig {

        @Value("${spring.cloud.loadbalancer.zone:unknown}")
        private String zone;

        @Bean
        public ServiceInstanceListSupplier serviceInstanceListSupplier(
                ConfigurableApplicationContext context) {
            
            // Build base supplier with discovery client only
            // NOT using withZonePreference() because it can't access podMetadata()
            ServiceInstanceListSupplier baseSupplier = 
                ServiceInstanceListSupplier.builder()
                    .withDiscoveryClient()
                    .build(context);
            
            // Wrap with our custom supplier that accesses podMetadata() for zone filtering
            return new LoggingServiceInstanceListSupplier(baseSupplier, zone);
        }
    }
}

