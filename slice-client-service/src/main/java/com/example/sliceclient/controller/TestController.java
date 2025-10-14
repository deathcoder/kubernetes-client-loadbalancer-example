package com.example.sliceclient.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
public class TestController {

    private final RestTemplate restTemplate;

    @Value("${spring.cloud.loadbalancer.zone:unknown}")
    private String clientZone;

    @Value("${HOSTNAME:unknown}")
    private String clientPod;

    public TestController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @GetMapping("/test-loadbalancing")
    public Map<String, Object> testLoadBalancing(@RequestParam(defaultValue = "1") int calls) {
        Map<String, Integer> podDistribution = new LinkedHashMap<>();
        Map<String, Integer> zoneDistribution = new LinkedHashMap<>();
        int sameZoneCalls = 0;
        int crossZoneCalls = 0;
        List<Map<String, Object>> callDetails = new ArrayList<>();

        for (int i = 0; i < calls; i++) {
            try {
                String url = "http://sample-service/info";
                @SuppressWarnings("unchecked")
                Map<String, String> response = restTemplate.getForObject(url, Map.class);
                
                if (response != null) {
                    String podName = response.get("podName");
                    String zoneName = response.get("zone");

                    podDistribution.merge(podName, 1, Integer::sum);
                    zoneDistribution.merge(zoneName, 1, Integer::sum);

                    boolean sameZone = clientZone.equalsIgnoreCase(zoneName);
                    if (sameZone) {
                        sameZoneCalls++;
                    } else {
                        crossZoneCalls++;
                    }

                    Map<String, Object> callDetail = new LinkedHashMap<>();
                    callDetail.put("pod", podName);
                    callDetail.put("callNumber", i + 1);
                    callDetail.put("zone", zoneName);
                    callDetail.put("sameZone", sameZone);
                    callDetails.add(callDetail);
                }
            } catch (Exception e) {
                Map<String, Object> callDetail = new LinkedHashMap<>();
                callDetail.put("callNumber", i + 1);
                callDetail.put("error", e.getMessage());
                callDetails.add(callDetail);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("clientZone", clientZone);
        result.put("clientPod", clientPod);
        result.put("totalCalls", calls);
        result.put("sameZoneCalls", sameZoneCalls);
        result.put("crossZoneCalls", crossZoneCalls);
        result.put("sameZonePercentage", String.format("%.1f%%", (double) sameZoneCalls / calls * 100));
        result.put("podDistribution", podDistribution);
        result.put("zoneDistribution", zoneDistribution);
        result.put("callDetails", callDetails);
        return result;
    }

    @GetMapping("/whoami")
    public String whoami() {
        return "Hello from slice-client-service pod: " + clientPod + " in zone: " + clientZone;
    }
}

