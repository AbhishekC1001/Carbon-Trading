package uk.ac.ed.acp.cw3.model;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TradeRepository extends JpaRepository<Trade, String> {
    List<Trade> findBySellerIdOrBuyerIdOrderByTimestampDesc(String sellerId, String buyerId);
    List<Trade> findTop20ByOrderByTimestampDesc();
}