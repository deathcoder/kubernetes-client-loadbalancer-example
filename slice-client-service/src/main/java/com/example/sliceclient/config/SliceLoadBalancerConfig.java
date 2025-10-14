package com.example.sliceclient.config;

import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClient;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@LoadBalancerClient(name = "sample-service", configuration = SliceLoadBalancerConfig.SampleServiceLoadBalancerConfig.class)
public class SliceLoadBalancerConfig {

    public static class SampleServiceLoadBalancerConfig {

        @Value("${spring.cloud.loadbalancer.zone:unknown}")
        private String clientZone;

        @Value("${spring.cloud.kubernetes.client.namespace:lb-demo}")
        private String namespace;

        @Bean
        public ServiceInstanceListSupplier serviceInstanceListSupplier(
                ConfigurableApplicationContext context) {

            // Build the base supplier with discovery client
            ServiceInstanceListSupplier delegate =
                    ServiceInstanceListSupplier.builder()
                            .withDiscoveryClient()
                            .build(context);

            // Get KubernetesClient
            KubernetesClient kubernetesClient = null;
            try {
                kubernetesClient = context.getBean(KubernetesClient.class);
            } catch (Exception e) {
                try {
                    if (context.getParent() != null) {
                        kubernetesClient = context.getParent().getBean(KubernetesClient.class);
                    }
                } catch (Exception ex) {
                    // KubernetesClient not available
                }
            }

            // Wrap with our EndpointSlice-based zone-aware supplier
            return new EndpointSliceZoneServiceInstanceListSupplier(
                    delegate,
                    clientZone,
                    kubernetesClient,
                    namespace);
        }
    }
}

