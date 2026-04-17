package uk.ac.ed.acp.cw3.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class AlertService {

    private static final Logger logger = LoggerFactory.getLogger(AlertService.class);

    private final Channel rabbitChannel;
    private final ObjectMapper mapper;
    private final RedisService redisService;

    @Value("${app.rabbitmq.alerts-queue}")
    private String alertsQueue;

    public AlertService(Channel rabbitChannel, ObjectMapper mapper, RedisService redisService) {
        this.rabbitChannel = rabbitChannel;
        this.mapper = mapper;
        this.redisService = redisService;
    }

    public void sendTradeMatchedAlert(String sellerId, String buyerId, int credits, double price) {
        Map<String, Object> alert = new LinkedHashMap<>();
        alert.put("type", "TRADE_MATCHED");
        alert.put("message", String.format("Trade matched: %s sold %d credits to %s at £%.2f",
                sellerId, credits, buyerId, price));
        alert.put("sellerId", sellerId);
        alert.put("buyerId", buyerId);
        alert.put("credits", credits);
        alert.put("price", price);
        alert.put("timestamp", Instant.now().toString());
        publishAlert(alert);
    }

    public void sendCapWarningAlert(String companyId, double percentUsed) {
        Map<String, Object> alert = new LinkedHashMap<>();
        alert.put("type", "CAP_WARNING");
        alert.put("message", String.format("WARNING: %s has used %.1f%% of emissions cap",
                companyId, percentUsed));
        alert.put("companyId", companyId);
        alert.put("percentUsed", percentUsed);
        alert.put("timestamp", Instant.now().toString());
        publishAlert(alert);
    }

    public void sendCapExceededAlert(String companyId) {
        Map<String, Object> alert = new LinkedHashMap<>();
        alert.put("type", "CAP_EXCEEDED");
        alert.put("message", String.format("CRITICAL: %s has EXCEEDED emissions cap! Must buy credits.",
                companyId));
        alert.put("companyId", companyId);
        alert.put("timestamp", Instant.now().toString());
        publishAlert(alert);
    }

    private void publishAlert(Map<String, Object> alert) {
        try {
            String json = mapper.writeValueAsString(alert);
            rabbitChannel.basicPublish("", alertsQueue, null, json.getBytes());
            redisService.addAlert(alert);
            logger.info("Alert published: {}", alert.get("message"));
        } catch (Exception e) {
            logger.error("Failed to publish alert", e);
        }
    }
}