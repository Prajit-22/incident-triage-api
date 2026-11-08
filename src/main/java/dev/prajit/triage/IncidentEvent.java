package dev.prajit.triage;

import java.time.Instant;
import javax.persistence.*;

@Entity
public class IncidentEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(nullable = false)
    public Long incidentId;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    public Kind kind;
    // Null on CREATED (there is no previous value); otherwise the value being replaced.
    public String fromValue;
    @Column(nullable = false)
    public String toValue;
    @Column(nullable = false)
    public Instant happenedAt;

    protected IncidentEvent() {}
    public IncidentEvent(Long incidentId, Kind kind, String fromValue, String toValue) {
        this.incidentId = incidentId;
        this.kind = kind;
        this.fromValue = fromValue;
        this.toValue = toValue;
        this.happenedAt = Instant.now();
    }
    public enum Kind { CREATED, STATUS_CHANGED, SEVERITY_CHANGED }
}
