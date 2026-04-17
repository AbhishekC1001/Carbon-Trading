package uk.ac.ed.acp.cw3.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "emissions_readings")
public class EmissionsReading {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String companyId;
    private double emissions;
    private Instant timestamp;

    public EmissionsReading() {}

    public EmissionsReading(String companyId, double emissions) {
        this.companyId = companyId;
        this.emissions = emissions;
        this.timestamp = Instant.now();
    }

    public Long getId() { return id; }
    public String getCompanyId() { return companyId; }
    public double getEmissions() { return emissions; }
    public Instant getTimestamp() { return timestamp; }
}