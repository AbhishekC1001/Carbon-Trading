package uk.ac.ed.acp.cw3.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "companies")
public class Company {

    @Id
    private String companyId;
    private String name;
    private double emissionsCap;

    public Company() {}

    public Company(String companyId, String name, double emissionsCap) {
        this.companyId = companyId;
        this.name = name;
        this.emissionsCap = emissionsCap;
    }

    public String getCompanyId() { return companyId; }
    public void setCompanyId(String companyId) { this.companyId = companyId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public double getEmissionsCap() { return emissionsCap; }
    public void setEmissionsCap(double emissionsCap) { this.emissionsCap = emissionsCap; }
}