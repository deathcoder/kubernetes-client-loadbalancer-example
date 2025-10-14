package com.example.simpleclient.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
public class TestController {

    @Autowired
    private RestTemplate restTemplate;

    @Value("${spring.cloud.loadbalancer.zone:unknown}")
    private String clientZone;

    @Value("${HOSTNAME:unknown}")
    private String hostname;

    @GetMapping("/test-loadbalancing")
    public Map<String, Object> testLoadBalancing(@RequestParam(defaultValue = "10") int calls) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Integer> podCounts = new HashMap<>();
        Map<String, Integer> zoneCounts = new HashMap<>();
        java.util.List<Map<String, Object>> callDetails = new java.util.ArrayList<>();

        result.put("clientZone", clientZone);
        result.put("clientPod", hostname);
        result.put("totalCalls", calls);

        int sameZoneCalls = 0;

        for (int i = 1; i <= calls; i++) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, String> response = restTemplate.getForObject(
                        "http://sample-service/info",
                        Map.class
                );

                String podName = response.get("podName");
                String zone = response.get("zone");

                podCounts.merge(podName, 1, Integer::sum);
                zoneCounts.merge(zone, 1, Integer::sum);

                boolean sameZone = clientZone.equals(zone);
                if (sameZone) {
                    sameZoneCalls++;
                }

                Map<String, Object> callDetail = new LinkedHashMap<>();
                callDetail.put("pod", podName);
                callDetail.put("callNumber", i);
                callDetail.put("zone", zone);
                callDetail.put("sameZone", sameZone);
                callDetails.add(callDetail);

            } catch (Exception e) {
                Map<String, Object> errorDetail = new LinkedHashMap<>();
                errorDetail.put("callNumber", i);
                errorDetail.put("error", e.getMessage());
                callDetails.add(errorDetail);
            }
        }

        result.put("sameZoneCalls", sameZoneCalls);
        result.put("crossZoneCalls", calls - sameZoneCalls);
        result.put("sameZonePercentage", String.format("%.1f%%", (sameZoneCalls * 100.0 / calls)));
        result.put("podDistribution", podCounts);
        result.put("zoneDistribution", zoneCounts);
        result.put("callDetails", callDetails);

        return result;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        Map<String, String> health = new HashMap<>();
        health.put("status", "UP");
        health.put("zone", clientZone);
        health.put("pod", hostname);
        return health;
    }
}

