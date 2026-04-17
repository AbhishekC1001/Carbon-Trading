package uk.ac.ed.acp.cw3.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.util.*;

@Service
public class RedisService {

    private static final Logger logger = LoggerFactory.getLogger(RedisService.class);

    private static final String BALANCE_PREFIX = "balance:";
    private static final String EMISSIONS_PREFIX = "emissions:";
    private static final String ORDER_BOOK = "orderbook";
    private static final String MARKET_PRICE = "market_price";
    private static final String ALERTS_LIST = "alerts";

    private final JedisPool jedisPool;
    private final ObjectMapper mapper;

    public RedisService(JedisPool jedisPool, ObjectMapper mapper) {
        this.jedisPool = jedisPool;
        this.mapper = mapper;
    }

    public void initCompanyBalance(String companyId, double cap) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.set(BALANCE_PREFIX + companyId, String.valueOf(cap));
            jedis.set(EMISSIONS_PREFIX + companyId, "0.0");
        }
    }

    public void addEmissions(String companyId, double emissions) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.incrByFloat(EMISSIONS_PREFIX + companyId, emissions);
        }
    }

    public void updateCreditBalance(String companyId, double newBalance) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.set(BALANCE_PREFIX + companyId, String.valueOf(newBalance));
        }
    }

    public double getCreditBalance(String companyId) {
        try (Jedis jedis = jedisPool.getResource()) {
            String val = jedis.get(BALANCE_PREFIX + companyId);
            return val != null ? Double.parseDouble(val) : 0.0;
        }
    }

    public double getTotalEmissions(String companyId) {
        try (Jedis jedis = jedisPool.getResource()) {
            String val = jedis.get(EMISSIONS_PREFIX + companyId);
            return val != null ? Double.parseDouble(val) : 0.0;
        }
    }

    public Map<String, Map<String, String>> getAllBalances() {
        try (Jedis jedis = jedisPool.getResource()) {
            Map<String, Map<String, String>> result = new LinkedHashMap<>();
            Set<String> keys = jedis.keys(BALANCE_PREFIX + "*");
            for (String key : keys) {
                String companyId = key.replace(BALANCE_PREFIX, "");
                Map<String, String> info = new HashMap<>();
                info.put("creditBalance", jedis.get(key));
                info.put("totalEmissions", jedis.get(EMISSIONS_PREFIX + companyId));
                result.put(companyId, info);
            }
            return result;
        }
    }


    public void addOrder(Map<String, Object> order) {
        try (Jedis jedis = jedisPool.getResource()) {
            String json = mapper.writeValueAsString(order);
            jedis.rpush(ORDER_BOOK, json);
        } catch (Exception e) {
            logger.error("Error adding order to Redis", e);
        }
    }

    public List<Map<String, Object>> getOrderBook() {
        try (Jedis jedis = jedisPool.getResource()) {
            List<String> orders = jedis.lrange(ORDER_BOOK, 0, -1);
            List<Map<String, Object>> result = new ArrayList<>();
            for (String json : orders) {
                result.add(mapper.readValue(json, new TypeReference<>() {}));
            }
            return result;
        } catch (Exception e) {
            logger.error("Error reading order book", e);
            return Collections.emptyList();
        }
    }

    public void setOrderBook(List<Map<String, Object>> orders) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.del(ORDER_BOOK);
            for (Map<String, Object> order : orders) {
                jedis.rpush(ORDER_BOOK, mapper.writeValueAsString(order));
            }
        } catch (Exception e) {
            logger.error("Error setting order book", e);
        }
    }

    public void setMarketPrice(double price) {
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.set(MARKET_PRICE, String.valueOf(price));
        }
    }

    public double getMarketPrice() {
        try (Jedis jedis = jedisPool.getResource()) {
            String val = jedis.get(MARKET_PRICE);
            return val != null ? Double.parseDouble(val) : 0.0;
        }
    }

    public void addAlert(Map<String, Object> alert) {
        try (Jedis jedis = jedisPool.getResource()) {
            String json = mapper.writeValueAsString(alert);
            jedis.lpush(ALERTS_LIST, json);
            jedis.ltrim(ALERTS_LIST, 0, 49);
        } catch (Exception e) {
            logger.error("Error adding alert", e);
        }
    }

    public List<Map<String, Object>> getAlerts() {
        try (Jedis jedis = jedisPool.getResource()) {
            List<String> alerts = jedis.lrange(ALERTS_LIST, 0, 19);
            List<Map<String, Object>> result = new ArrayList<>();
            for (String json : alerts) {
                result.add(mapper.readValue(json, new TypeReference<>() {}));
            }
            return result;
        } catch (Exception e) {
            logger.error("Error reading alerts", e);
            return Collections.emptyList();
        }
    }
}