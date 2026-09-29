package dev.prajit.triage;

import java.time.Instant;
import javax.persistence.*;

@Entity
public class Incident {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(nullable = false, length = 120)
    public String title;
    @Column(nullable = false, length = 2000)
    public String description;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    public Severity severity;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    public Status status;
    @Column(nullable = false)
    public Instant createdAt;

    protected Incident() {}
    public Incident(String title, String description, Severity severity) {
        this.title = title;
        this.description = description;
        this.severity = severity;
        this.status = Status.OPEN;
        this.createdAt = Instant.now();
    }
    public enum Severity { LOW, MEDIUM, HIGH, CRITICAL }
    public enum Status { OPEN, INVESTIGATING, RESOLVED }
}
