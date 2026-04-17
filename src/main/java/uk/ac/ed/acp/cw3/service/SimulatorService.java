package uk.ac.ed.acp.cw3.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class SimulatorService {

    private static final Logger logger = LoggerFactory.getLogger(SimulatorService.class);

    private final KafkaProducer<String, String> kafkaProducer;
    private final ObjectMapper mapper;

    @Value("${app.kafka.emissions-topic}")
    private String emissionsTopic;

    private Thread simulatorThread;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Random random = new Random();

    private static final Map<String, Double> COMPANY_BASE_RATES = new LinkedHashMap<>();
    static {
        COMPANY_BASE_RATES.put("SHELL", 45.0);
        COMPANY_BASE_RATES.put("BP", 38.0);
        COMPANY_BASE_RATES.put("ENGIE", 25.0);
        COMPANY_BASE_RATES.put("ARAMCO", 55.0);
        COMPANY_BASE_RATES.put("TOTAL", 32.0);
    }

    public SimulatorService(KafkaProducer<String, String> kafkaProducer, ObjectMapper mapper) {
        this.kafkaProducer = kafkaProducer;
        this.mapper = mapper;
    }

    public boolean isRunning() {
        return running.get();
    }

    public void start() {
        if (running.get()) {
            logger.info("Simulator already running");
            return;
        }
        running.set(true);
        simulatorThread = new Thread(this::simulate, "emissions-simulator");
        simulatorThread.setDaemon(true);
        simulatorThread.start();
        logger.info("Emissions simulator started");
    }

    public void stop() {
        running.set(false);
        logger.info("Emissions simulator stopped");
    }

    private void simulate() {
        while (running.get()) {
            try {
                List<String> companies = new ArrayList<>(COMPANY_BASE_RATES.keySet());
                String companyId = companies.get(random.nextInt(companies.size()));
                double baseRate = COMPANY_BASE_RATES.get(companyId);

                double emissions = baseRate * (0.7 + random.nextDouble() * 0.6);
                emissions = Math.round(emissions * 100.0) / 100.0;

                Map<String, Object> reading = new LinkedHashMap<>();
                reading.put("companyId", companyId);
                reading.put("emissions", emissions);
                reading.put("timestamp", Instant.now().toString());

                String json = mapper.writeValueAsString(reading);
                kafkaProducer.send(new ProducerRecord<>(emissionsTopic, companyId, json));
                kafkaProducer.flush();

                logger.debug("Simulated emission: {} -> {} tonnes", companyId, emissions);

                // Wait 2-4 seconds before next reading
                Thread.sleep(2000 + random.nextInt(2000));

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                logger.error("Simulator error", e);
            }
        }
    }
}