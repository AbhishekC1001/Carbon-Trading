package uk.ac.ed.acp.cw3.model;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface EmissionsReadingRepository extends JpaRepository<EmissionsReading, Long> {
    List<EmissionsReading> findByCompanyIdOrderByTimestampDesc(String companyId);
}