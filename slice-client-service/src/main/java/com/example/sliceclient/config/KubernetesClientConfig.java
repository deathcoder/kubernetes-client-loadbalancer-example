package com.example.sliceclient.config;

import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KubernetesClientConfig {

    @Value("${spring.cloud.kubernetes.client.namespace:lb-demo}")
    private String namespace;

    @Bean
    public KubernetesClient kubernetesClient() {
        Config config = new ConfigBuilder().withNamespace(namespace).build();
        return new DefaultKubernetesClient(config);
    }
}

