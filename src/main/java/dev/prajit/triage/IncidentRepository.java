package dev.prajit.triage;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface IncidentRepository extends JpaRepository<Incident, Long> {
    List<Incident> findAllByStatus(Incident.Status status);
    long countByStatus(Incident.Status status);
    long countBySeverity(Incident.Severity severity);

    // List query: every filter optional; null means "do not filter on this".
    // `query` is a case-insensitive substring match over title and description.
    // createdAfter/createdBefore bound the createdAt timestamp, both ends
    // inclusive, so a boundary incident lands in both adjacent windows.
    @Query("SELECT i FROM Incident i WHERE "
        + "(:status IS NULL OR i.status = :status) AND "
        + "(:severity IS NULL OR i.severity = :severity) AND "
        + "(:query IS NULL OR LOWER(i.title) LIKE LOWER(CONCAT(CONCAT('%', :query), '%')) "
        + "OR LOWER(i.description) LIKE LOWER(CONCAT(CONCAT('%', :query), '%'))) AND "
        + "(:createdAfter IS NULL OR i.createdAt >= :createdAfter) AND "
        + "(:createdBefore IS NULL OR i.createdAt <= :createdBefore)")
    Page<Incident> search(@Param("status") Incident.Status status,
                          @Param("severity") Incident.Severity severity,
                          @Param("query") String query,
                          @Param("createdAfter") java.time.Instant createdAfter,
                          @Param("createdBefore") java.time.Instant createdBefore,
                          Pageable pageable);
}
