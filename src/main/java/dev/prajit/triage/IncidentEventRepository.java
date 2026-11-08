package dev.prajit.triage;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IncidentEventRepository extends JpaRepository<IncidentEvent, Long> {
    // Chronological per-incident history; id breaks timestamp ties in write order.
    List<IncidentEvent> findByIncidentIdOrderByHappenedAtAscIdAsc(Long incidentId);
}
