package com.example.sampleservice.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;

@RestController
public class InfoController {

    @Value("${spring.application.name:sample-service}")
    private String applicationName;

    @Value("${ZONE:unknown}")
    private String zone;

    @Value("${POD_NAME:local}")
    private String podName;

    @Value("${POD_IP:127.0.0.1}")
    private String podIp;

    @GetMapping("/info")
    public Map<String, String> getInfo() throws UnknownHostException {
        Map<String, String> info = new HashMap<>();
        info.put("application", applicationName);
        info.put("zone", zone);
        info.put("podName", podName);
        info.put("podIp", podIp);
        info.put("hostname", InetAddress.getLocalHost().getHostName());
        info.put("timestamp", String.valueOf(System.currentTimeMillis()));
        return info;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        Map<String, String> health = new HashMap<>();
        health.put("status", "UP");
        health.put("zone", zone);
        return health;
    }
}

