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

    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public Incident create(@Valid @RequestBody CreateRequest request) {
        return repository.save(new Incident(request.title, request.description, request.severity));
    }

    @GetMapping("/{id}")
    public Incident get(@PathVariable Long id) {
        return repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Incident not found"));
    }

    @GetMapping
    public Page<Incident> list(@RequestParam(required = false) Incident.Status status,
                               @RequestParam(required = false) Incident.Severity severity,
                               @RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be >= 0; size must be 1..100");
        PageRequest paging = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"));
        if (status != null && severity != null) return repository.findByStatusAndSeverity(status, severity, paging);
        if (status != null) return repository.findByStatus(status, paging);
        if (severity != null) return repository.findBySeverity(severity, paging);
        return repository.findAll(paging);
    }

    @PatchMapping("/{id}/status")
    public Incident updateStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        Incident incident = get(id);
        if (incident.status == Incident.Status.RESOLVED && request.status != Incident.Status.RESOLVED)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Resolved incidents cannot be reopened");
        incident.status = request.status;
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
