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
    // Open work past its deadline; resolved incidents are never overdue.
    long countByDueAtBeforeAndStatusNot(java.time.Instant now, Incident.Status status);

    // List query: every filter optional; null means "do not filter on this".
    // `query` is a case-insensitive substring match over title and description.
    // createdAfter/createdBefore bound the createdAt timestamp, both ends
    // inclusive, so a boundary incident lands in both adjacent windows.
    // overdue=true keeps only open incidents past their dueAt (null dueAt is
    // never overdue); overdue=false keeps everything else.
    @Query("SELECT i FROM Incident i WHERE "
        + "(:status IS NULL OR i.status = :status) AND "
        + "(:severity IS NULL OR i.severity = :severity) AND "
        + "(:query IS NULL OR LOWER(i.title) LIKE LOWER(CONCAT(CONCAT('%', :query), '%')) "
        + "OR LOWER(i.description) LIKE LOWER(CONCAT(CONCAT('%', :query), '%'))) AND "
        + "(:createdAfter IS NULL OR i.createdAt >= :createdAfter) AND "
        + "(:createdBefore IS NULL OR i.createdAt <= :createdBefore) AND "
        + "(:overdue IS NULL OR "
        + "(:overdue = TRUE AND i.dueAt IS NOT NULL AND i.dueAt < :now AND i.status <> :resolvedStatus) OR "
        + "(:overdue = FALSE AND (i.dueAt IS NULL OR i.dueAt >= :now OR i.status = :resolvedStatus)))")
    Page<Incident> search(@Param("status") Incident.Status status,
                          @Param("severity") Incident.Severity severity,
                          @Param("query") String query,
                          @Param("createdAfter") java.time.Instant createdAfter,
                          @Param("createdBefore") java.time.Instant createdBefore,
                          @Param("overdue") Boolean overdue,
                          @Param("now") java.time.Instant now,
                          @Param("resolvedStatus") Incident.Status resolvedStatus,
                          Pageable pageable);
}
