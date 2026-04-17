package uk.ac.ed.acp.cw3.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import uk.ac.ed.acp.cw3.model.*;
import uk.ac.ed.acp.cw3.service.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/carbon")
@CrossOrigin(origins = "*")
public class CarbonController {

    private static final Logger logger = LoggerFactory.getLogger(CarbonController.class);

    private final RedisService redisService;
    private final TradingService tradingService;
    private final SimulatorService simulatorService;
    private final CompanyRepository companyRepository;
    private final TradeRepository tradeRepository;

    public CarbonController(RedisService redisService, TradingService tradingService,
                            SimulatorService simulatorService, CompanyRepository companyRepository,
                            TradeRepository tradeRepository) {
        this.redisService = redisService;
        this.tradingService = tradingService;
        this.simulatorService = simulatorService;
        this.companyRepository = companyRepository;
        this.tradeRepository = tradeRepository;
    }

    @PostMapping("/company")
    public ResponseEntity<?> registerCompany(@RequestBody Map<String, Object> body) {
        String companyId = (String) body.get("companyId");
        String name = (String) body.get("name");
        double emissionsCap = ((Number) body.get("emissionsCap")).doubleValue();

        Company company = new Company(companyId, name, emissionsCap);
        companyRepository.save(company);
        redisService.initCompanyBalance(companyId, emissionsCap);

        logger.info("Company registered: {} ({}) with cap {} tonnes", companyId, name, emissionsCap);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "REGISTERED");
        result.put("company", company);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/companies")
    public ResponseEntity<?> getCompanies() {
        List<Company> companies = companyRepository.findAll();
        List<Map<String, Object>> enriched = new ArrayList<>();

        for (Company c : companies) {
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("companyId", c.getCompanyId());
            info.put("name", c.getName());
            info.put("emissionsCap", c.getEmissionsCap());
            info.put("totalEmissions", redisService.getTotalEmissions(c.getCompanyId()));
            info.put("creditBalance", redisService.getCreditBalance(c.getCompanyId()));
            enriched.add(info);
        }
        return ResponseEntity.ok(enriched);
    }


    @PostMapping("/order")
    public ResponseEntity<?> placeOrder(@RequestBody Map<String, Object> body) {
        String companyId = (String) body.get("companyId");
        String type = ((String) body.get("type")).toUpperCase();
        int credits = ((Number) body.get("credits")).intValue();
        double price = ((Number) body.get("price")).doubleValue();

        if (!type.equals("BUY") && !type.equals("SELL")) {
            return ResponseEntity.badRequest().body(Map.of("error", "type must be BUY or SELL"));
        }

        Map<String, Object> result = tradingService.placeOrder(companyId, type, credits, price);

        if (result.containsKey("error")) {
            return ResponseEntity.badRequest().body(result);
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/orderbook")
    public ResponseEntity<?> getOrderBook() {
        return ResponseEntity.ok(redisService.getOrderBook());
    }


    @GetMapping("/balance/{companyId}")
    public ResponseEntity<?> getBalance(@PathVariable String companyId) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("companyId", companyId);
        result.put("creditBalance", redisService.getCreditBalance(companyId));
        result.put("totalEmissions", redisService.getTotalEmissions(companyId));

        Optional<Company> company = companyRepository.findById(companyId);
        company.ifPresent(c -> {
            result.put("emissionsCap", c.getEmissionsCap());
            double percentUsed = (redisService.getTotalEmissions(companyId) / c.getEmissionsCap()) * 100;
            result.put("percentUsed", Math.round(percentUsed * 10.0) / 10.0);
        });

        return ResponseEntity.ok(result);
    }

    @GetMapping("/balances")
    public ResponseEntity<?> getAllBalances() {
        return ResponseEntity.ok(redisService.getAllBalances());
    }


    @GetMapping("/trades")
    public ResponseEntity<?> getRecentTrades() {
        return ResponseEntity.ok(tradeRepository.findTop20ByOrderByTimestampDesc());
    }

    @GetMapping("/trades/{companyId}")
    public ResponseEntity<?> getCompanyTrades(@PathVariable String companyId) {
        return ResponseEntity.ok(
                tradeRepository.findBySellerIdOrBuyerIdOrderByTimestampDesc(companyId, companyId));
    }


    @GetMapping("/price")
    public ResponseEntity<?> getMarketPrice() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("currentPrice", redisService.getMarketPrice());
        result.put("currency", "GBP");
        return ResponseEntity.ok(result);
    }


    @GetMapping("/alerts")
    public ResponseEntity<?> getAlerts() {
        return ResponseEntity.ok(redisService.getAlerts());
    }


    @PostMapping("/simulate/start")
    public ResponseEntity<?> startSimulator() {
        simulatorService.start();
        return ResponseEntity.ok(Map.of("status", "SIMULATOR_STARTED"));
    }

    @PostMapping("/simulate/stop")
    public ResponseEntity<?> stopSimulator() {
        simulatorService.stop();
        return ResponseEntity.ok(Map.of("status", "SIMULATOR_STOPPED"));
    }

    @GetMapping("/simulate/status")
    public ResponseEntity<?> simulatorStatus() {
        return ResponseEntity.ok(Map.of("running", simulatorService.isRunning()));
    }
}