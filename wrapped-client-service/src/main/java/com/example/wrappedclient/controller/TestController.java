package com.example.wrappedclient.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@RestController
public class TestController {

    private static final Logger log = LoggerFactory.getLogger(TestController.class);

    private final RestTemplate restTemplate;
    private final String clientZone;
    private final String podName;

    public TestController(
            RestTemplate restTemplate,
            @Value("${spring.cloud.loadbalancer.zone:unknown}") String clientZone,
            @Value("${POD_NAME:unknown}") String podName) {
        this.restTemplate = restTemplate;
        this.clientZone = clientZone;
        this.podName = podName;
    }

    @GetMapping("/test-loadbalancing")
    public Map<String, Object> testLoadBalancing(@RequestParam(defaultValue = "10") int calls) {
        log.info("=".repeat(80));
        log.info("TESTING LOAD BALANCING WITH WRAPPED DISCOVERY CLIENT APPROACH");
        log.info("Client Zone: {}, Client Pod: {}, Calls: {}", clientZone, podName, calls);
        log.info("=".repeat(80));

        Map<String, Integer> podDistribution = new HashMap<>();
        Map<String, Integer> zoneDistribution = new HashMap<>();
        List<Map<String, Object>> callDetails = new ArrayList<>();
        int sameZoneCalls = 0;
        int crossZoneCalls = 0;

        for (int i = 1; i <= calls; i++) {
            try {
                String url = "http://sample-service/info";
                @SuppressWarnings("unchecked")
                Map<String, String> response = restTemplate.getForObject(url, Map.class);

                if (response != null) {
                    String targetPod = response.get("podName");
                    String targetZone = response.get("zone");

                    podDistribution.merge(targetPod, 1, Integer::sum);
                    zoneDistribution.merge(targetZone, 1, Integer::sum);

                    boolean sameZone = clientZone.equals(targetZone);
                    if (sameZone) {
                        sameZoneCalls++;
                    } else {
                        crossZoneCalls++;
                    }

                    Map<String, Object> callDetail = new LinkedHashMap<>();
                    callDetail.put("pod", targetPod);
                    callDetail.put("callNumber", i);
                    callDetail.put("zone", targetZone);
                    callDetail.put("sameZone", sameZone);
                    callDetails.add(callDetail);

                    log.info("Call {}: Routed to pod {} in zone {} (same zone: {})",
                            i, targetPod, targetZone, sameZone);
                }
            } catch (Exception e) {
                log.error("Error during call {}: {}", i, e.getMessage());
            }
        }

        double sameZonePercentage = (calls > 0) ? (sameZoneCalls * 100.0 / calls) : 0;

        log.info("=".repeat(80));
        log.info("LOAD BALANCING TEST RESULTS (Wrapped DiscoveryClient Approach)");
        log.info("Total Calls: {}", calls);
        log.info("Same Zone Calls: {} ({}%)", sameZoneCalls, String.format("%.1f", sameZonePercentage));
        log.info("Cross Zone Calls: {}", crossZoneCalls);
        log.info("Pod Distribution: {}", podDistribution);
        log.info("Zone Distribution: {}", zoneDistribution);
        log.info("=".repeat(80));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("clientZone", clientZone);
        result.put("clientPod", podName);
        result.put("totalCalls", calls);
        result.put("sameZoneCalls", sameZoneCalls);
        result.put("crossZoneCalls", crossZoneCalls);
        result.put("sameZonePercentage", String.format("%.1f%%", sameZonePercentage));
        result.put("podDistribution", podDistribution);
        result.put("zoneDistribution", zoneDistribution);
        result.put("callDetails", callDetails);

        return result;
    }
}

