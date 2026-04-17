package uk.ac.ed.acp.cw3.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "trades")
public class Trade {

    @Id
    private String tradeId;
    private String sellerId;
    private String buyerId;
    private int credits;
    private double price;
    private Instant timestamp;

    public Trade() {}

    public Trade(String tradeId, String sellerId, String buyerId, int credits, double price) {
        this.tradeId = tradeId;
        this.sellerId = sellerId;
        this.buyerId = buyerId;
        this.credits = credits;
        this.price = price;
        this.timestamp = Instant.now();
    }

    public String getTradeId() { return tradeId; }
    public String getSellerId() { return sellerId; }
    public String getBuyerId() { return buyerId; }
    public int getCredits() { return credits; }
    public double getPrice() { return price; }
    public Instant getTimestamp() { return timestamp; }

    public void setTradeId(String tradeId) { this.tradeId = tradeId; }
    public void setSellerId(String sellerId) { this.sellerId = sellerId; }
    public void setBuyerId(String buyerId) { this.buyerId = buyerId; }
    public void setCredits(int credits) { this.credits = credits; }
    public void setPrice(double price) { this.price = price; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }
}