package dev.prajit.triage;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentRepository extends JpaRepository<Incident, Long> {
    Page<Incident> findByStatus(Incident.Status status, Pageable pageable);
    Page<Incident> findBySeverity(Incident.Severity severity, Pageable pageable);
    Page<Incident> findByStatusAndSeverity(Incident.Status status, Incident.Severity severity, Pageable pageable);
}
