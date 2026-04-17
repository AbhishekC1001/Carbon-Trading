package uk.ac.ed.acp.cw3.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.ac.ed.acp.cw3.model.Company;
import uk.ac.ed.acp.cw3.model.CompanyRepository;
import uk.ac.ed.acp.cw3.model.EmissionsReading;
import uk.ac.ed.acp.cw3.model.EmissionsReadingRepository;

import java.time.Duration;
import java.util.Collections;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class EmissionsService {

    private static final Logger logger = LoggerFactory.getLogger(EmissionsService.class);

    private final Properties kafkaConsumerProperties;
    private final ObjectMapper mapper;
    private final RedisService redisService;
    private final AlertService alertService;
    private final CompanyRepository companyRepository;
    private final EmissionsReadingRepository emissionsReadingRepository;

    @Value("${app.kafka.emissions-topic}")
    private String emissionsTopic;

    private KafkaConsumer<String, String> consumer;
    private Thread consumerThread;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public EmissionsService(Properties kafkaConsumerProperties, ObjectMapper mapper,
                            RedisService redisService, AlertService alertService,
                            CompanyRepository companyRepository,
                            EmissionsReadingRepository emissionsReadingRepository) {
        this.kafkaConsumerProperties = kafkaConsumerProperties;
        this.mapper = mapper;
        this.redisService = redisService;
        this.alertService = alertService;
        this.companyRepository = companyRepository;
        this.emissionsReadingRepository = emissionsReadingRepository;
    }

    @PostConstruct
    public void startConsumer() {
        running.set(true);
        consumerThread = new Thread(this::consumeEmissions, "emissions-consumer");
        consumerThread.setDaemon(true);
        consumerThread.start();
        logger.info("Emissions consumer started for topic '{}'", emissionsTopic);
    }

    @PreDestroy
    public void stopConsumer() {
        running.set(false);
        if (consumer != null) {
            consumer.wakeup();
        }
    }

    private void consumeEmissions() {
        try {
            consumer = new KafkaConsumer<>(kafkaConsumerProperties);
            consumer.subscribe(Collections.singletonList(emissionsTopic));

            while (running.get()) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, String> record : records) {
                    try {
                        processEmission(record.value());
                    } catch (Exception e) {
                        logger.error("Error processing emission: {}", record.value(), e);
                    }
                }
            }
        } catch (Exception e) {
            if (running.get()) {
                logger.error("Emissions consumer error", e);
            }
        } finally {
            if (consumer != null) {
                consumer.close();
            }
        }
    }

    private void processEmission(String message) throws Exception {
        JsonNode node = mapper.readTree(message);
        String companyId = node.get("companyId").asText();
        double emissions = node.get("emissions").asDouble();

        emissionsReadingRepository.save(new EmissionsReading(companyId, emissions));

        redisService.addEmissions(companyId, emissions);
        double totalEmissions = redisService.getTotalEmissions(companyId);

        Optional<Company> companyOpt = companyRepository.findById(companyId);
        if (companyOpt.isPresent()) {
            Company company = companyOpt.get();
            double cap = company.getEmissionsCap();
            double remainingCredits = cap - totalEmissions;
            redisService.updateCreditBalance(companyId, remainingCredits);

            double percentUsed = (totalEmissions / cap) * 100;

            if (percentUsed >= 100) {
                alertService.sendCapExceededAlert(companyId);
            } else if (percentUsed >= 80) {
                alertService.sendCapWarningAlert(companyId, percentUsed);
            }

            logger.debug("{}: +{} emissions, total={}, remaining={}, used={}%",
                    companyId, emissions, totalEmissions, remainingCredits,
                    String.format("%.1f", percentUsed));
        }
    }
}