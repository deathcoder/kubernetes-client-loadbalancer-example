package com.example.clientservice.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
public class TestController {

    private final RestTemplate restTemplate;
    
    @Value("${ZONE:unknown}")
    private String clientZone;

    @Value("${POD_NAME:local-client}")
    private String podName;

    public TestController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Call the sample-service and return its info
     */
    @GetMapping("/call-service")
    public Map<String, Object> callService() {
        try {
            // Using service name for load balancing
            String url = "http://sample-service/info";
            @SuppressWarnings("unchecked")
            Map<String, String> response = restTemplate.getForObject(url, Map.class);
            
            Map<String, Object> result = new HashMap<>();
            result.put("clientZone", clientZone);
            result.put("clientPod", podName);
            result.put("serviceResponse", response);
            result.put("samezoneCall", clientZone.equals(response.get("zone")));
            
            return result;
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("error", e.getMessage());
            error.put("clientZone", clientZone);
            return error;
        }
    }

    /**
     * Test load balancing by making multiple calls and tracking which pods respond
     */
    @GetMapping("/test-loadbalancing")
    public Map<String, Object> testLoadBalancing(@RequestParam(defaultValue = "10") int calls) {
        Map<String, Integer> podCounts = new ConcurrentHashMap<>();
        Map<String, Integer> zoneCounts = new ConcurrentHashMap<>();
        List<Map<String, Object>> callDetails = new ArrayList<>();
        int sameZoneCalls = 0;

        for (int i = 0; i < calls; i++) {
            try {
                String url = "http://sample-service/info";
                @SuppressWarnings("unchecked")
                Map<String, String> response = restTemplate.getForObject(url, Map.class);
                
                if (response != null) {
                    String podName = response.get("podName");
                    String zone = response.get("zone");
                    
                    podCounts.merge(podName, 1, Integer::sum);
                    zoneCounts.merge(zone, 1, Integer::sum);
                    
                    boolean sameZone = clientZone.equals(zone);
                    if (sameZone) {
                        sameZoneCalls++;
                    }
                    
                    Map<String, Object> detail = new HashMap<>();
                    detail.put("callNumber", i + 1);
                    detail.put("pod", podName);
                    detail.put("zone", zone);
                    detail.put("sameZone", sameZone);
                    callDetails.add(detail);
                }
            } catch (Exception e) {
                Map<String, Object> detail = new HashMap<>();
                detail.put("callNumber", i + 1);
                detail.put("error", e.getMessage());
                callDetails.add(detail);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("clientZone", clientZone);
        result.put("clientPod", this.podName);
        result.put("totalCalls", calls);
        result.put("sameZoneCalls", sameZoneCalls);
        result.put("crossZoneCalls", calls - sameZoneCalls);
        result.put("sameZonePercentage", (sameZoneCalls * 100.0 / calls) + "%");
        result.put("podDistribution", podCounts);
        result.put("zoneDistribution", zoneCounts);
        result.put("callDetails", callDetails);

        return result;
    }

    @GetMapping("/client-info")
    public Map<String, String> getClientInfo() {
        Map<String, String> info = new HashMap<>();
        info.put("zone", clientZone);
        info.put("podName", podName);
        return info;
    }
}

