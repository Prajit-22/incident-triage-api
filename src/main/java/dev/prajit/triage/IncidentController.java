package dev.prajit.triage;

import java.util.Map;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/incidents")
public class IncidentController {
    private final IncidentRepository repository;
    public IncidentController(IncidentRepository repository) { this.repository = repository; }

    public static class CreateRequest {
        @NotBlank @Size(max = 120) public String title;
        @NotBlank @Size(max = 2000) public String description;
        @NotNull public Incident.Severity severity;
    }
    public static class StatusRequest {
        @NotNull public Incident.Status status;
    }
    public static class SeverityRequest {
        @NotNull public Incident.Severity severity;
    }

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public Incident create(@Valid @RequestBody CreateRequest request) {
        return repository.save(new Incident(request.title, request.description, request.severity));
    }

    @GetMapping("/{id}")
    public Incident get(@PathVariable Long id) {
        return repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found"));
    }

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        Map<String, Long> byStatus = new java.util.LinkedHashMap<>();
        for (Incident.Status s : Incident.Status.values()) byStatus.put(s.name(), repository.countByStatus(s));
        Map<String, Long> bySeverity = new java.util.LinkedHashMap<>();
        for (Incident.Severity s : Incident.Severity.values()) bySeverity.put(s.name(), repository.countBySeverity(s));
        Map<String, Object> result = new java.util.LinkedHashMap<>();
        result.put("total", repository.count());
        result.put("byStatus", byStatus);
        result.put("bySeverity", bySeverity);
        result.put("averageResolutionSeconds", averageResolutionSeconds());
        return result;
    }

    private Double averageResolutionSeconds() {
        // Mean created-to-resolved time over resolved incidents, null when none are resolved.
        double total = 0;
        long count = 0;
        for (Incident incident : repository.findAllByStatus(Incident.Status.RESOLVED)) {
            if (incident.resolvedAt == null) continue; // rows resolved before this field existed
            total += java.time.Duration.between(incident.createdAt, incident.resolvedAt).toMillis() / 1000.0;
            count++;
        }
        return count == 0 ? null : Math.round(total / count * 10.0) / 10.0;
    }

    @GetMapping
    public Page<Incident> list(@RequestParam(required = false) Incident.Status status,
                               @RequestParam(required = false) Incident.Severity severity,
                               @RequestParam(required = false) String q,
                               @RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be >= 0; size must be 1..100");
        if ((long) page * size > Integer.MAX_VALUE)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page * size must be <= 2147483647");
        if (q != null && q.isBlank()) q = null; // a blank keyword is no keyword
        PageRequest paging = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        return repository.search(status, severity, q, paging);
    }

    @PatchMapping("/{id}/status")
    public Incident updateStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        Incident incident = get(id);
        if (incident.status == Incident.Status.RESOLVED && request.status != Incident.Status.RESOLVED)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Resolved incidents cannot be reopened");
        if (request.status == Incident.Status.RESOLVED && incident.resolvedAt == null)
            incident.resolvedAt = java.time.Instant.now();
        incident.status = request.status;
        return repository.save(incident);
    }

    @PatchMapping("/{id}/severity")
    public Incident updateSeverity(@PathVariable Long id, @Valid @RequestBody SeverityRequest request) {
        // Severity is an assessment label, not lifecycle: re-triage is allowed
        // at any point, including after resolution (status stays untouched).
        Incident incident = get(id);
        incident.severity = request.severity;
        return repository.save(incident);
    }

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> validationError(org.springframework.web.bind.MethodArgumentNotValidException ex) {
        Map<String, String> fields = new java.util.TreeMap<>();
        ex.getBindingResult().getFieldErrors()
            .forEach(e -> fields.putIfAbsent(e.getField(), e.getDefaultMessage()));
        return Map.of("error", "Invalid incident payload", "fields", fields);
    }
}
