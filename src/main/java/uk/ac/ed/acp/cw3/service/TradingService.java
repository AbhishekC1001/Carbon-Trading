package uk.ac.ed.acp.cw3.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import uk.ac.ed.acp.cw3.model.Trade;
import uk.ac.ed.acp.cw3.model.TradeRepository;

import java.util.*;

@Service
public class TradingService {

    private static final Logger logger = LoggerFactory.getLogger(TradingService.class);

    private final RedisService redisService;
    private final AlertService alertService;
    private final TradeRepository tradeRepository;

    public TradingService(RedisService redisService, AlertService alertService, TradeRepository tradeRepository) {
        this.redisService = redisService;
        this.alertService = alertService;
        this.tradeRepository = tradeRepository;
    }

    public Map<String, Object> placeOrder(String companyId, String type, int credits, double price) {
        if (type.equals("SELL")) {
            double balance = redisService.getCreditBalance(companyId);
            if (balance < credits) {
                Map<String, Object> error = new LinkedHashMap<>();
                error.put("error", "Insufficient credits");
                error.put("available", balance);
                error.put("requested", credits);
                return error;
            }
        }

        Map<String, Object> order = new LinkedHashMap<>();
        order.put("orderId", UUID.randomUUID().toString());
        order.put("companyId", companyId);
        order.put("type", type);
        order.put("credits", credits);
        order.put("remainingCredits", credits);
        order.put("price", price);
        order.put("timestamp", System.currentTimeMillis());

        redisService.addOrder(order);
        logger.info("Order placed: {} {} {} credits at £{}", companyId, type, credits, price);

        // Try to match after adding
        List<Trade> matches = tryMatch();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "ORDER_PLACED");
        result.put("order", order);
        result.put("matchesExecuted", matches.size());
        if (!matches.isEmpty()) {
            result.put("trades", matches);
        }
        return result;
    }

    private List<Trade> tryMatch() {
        List<Map<String, Object>> orders = redisService.getOrderBook();
        List<Trade> executedTrades = new ArrayList<>();

        List<Map<String, Object>> buys = new ArrayList<>();
        List<Map<String, Object>> sells = new ArrayList<>();

        for (Map<String, Object> order : orders) {
            int remaining = getInt(order, "remainingCredits");
            if (remaining <= 0) continue;

            if ("BUY".equals(order.get("type"))) {
                buys.add(order);
            } else {
                sells.add(order);
            }
        }

        buys.sort((a, b) -> Double.compare(getDouble(b, "price"), getDouble(a, "price")));
        sells.sort((a, b) -> Double.compare(getDouble(a, "price"), getDouble(b, "price")));

        for (Map<String, Object> buy : buys) {
            for (Map<String, Object> sell : sells) {
                int buyRemaining = getInt(buy, "remainingCredits");
                int sellRemaining = getInt(sell, "remainingCredits");
                if (buyRemaining <= 0 || sellRemaining <= 0) continue;

                String buyCompany = (String) buy.get("companyId");
                String sellCompany = (String) sell.get("companyId");
                if (buyCompany.equals(sellCompany)) continue;

                double buyPrice = getDouble(buy, "price");
                double sellPrice = getDouble(sell, "price");

                if (buyPrice >= sellPrice) {
                    double matchPrice = sellPrice;
                    int matchCredits = Math.min(buyRemaining, sellRemaining);

                    Trade trade = executeTrade(sellCompany, buyCompany, matchCredits, matchPrice);
                    executedTrades.add(trade);

                    buy.put("remainingCredits", buyRemaining - matchCredits);
                    sell.put("remainingCredits", sellRemaining - matchCredits);
                }
            }
        }

        // Remove filled orders
        List<Map<String, Object>> updatedOrders = new ArrayList<>();
        for (Map<String, Object> order : orders) {
            if (getInt(order, "remainingCredits") > 0) {
                updatedOrders.add(order);
            }
        }
        redisService.setOrderBook(updatedOrders);

        return executedTrades;
    }

    private Trade executeTrade(String sellerId, String buyerId, int credits, double price) {
        double sellerBalance = redisService.getCreditBalance(sellerId);
        double buyerBalance = redisService.getCreditBalance(buyerId);
        redisService.updateCreditBalance(sellerId, sellerBalance - credits);
        redisService.updateCreditBalance(buyerId, buyerBalance + credits);

        redisService.setMarketPrice(price);

        Trade trade = new Trade(UUID.randomUUID().toString(), sellerId, buyerId, credits, price);
        tradeRepository.save(trade);

        alertService.sendTradeMatchedAlert(sellerId, buyerId, credits, price);

        logger.info("Trade executed: {} -> {} | {} credits @ £{}", sellerId, buyerId, credits, price);
        return trade;
    }

    private int getInt(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Integer) return (Integer) val;
        if (val instanceof Number) return ((Number) val).intValue();
        return Integer.parseInt(val.toString());
    }

    private double getDouble(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Double) return (Double) val;
        if (val instanceof Number) return ((Number) val).doubleValue();
        return Double.parseDouble(val.toString());
    }
}