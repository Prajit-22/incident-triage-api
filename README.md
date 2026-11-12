# Incident Triage API

A small Spring Boot REST API for tracking security incidents. It stores incidents in an H2 database, validates input, filters and paginates results, and enforces a simple lifecycle rule: a resolved incident cannot be reopened. Built as a learning project with Java 11, Spring Boot 2.7, Spring Data JPA, and MockMvc integration tests.

## Run

Prerequisites: Java 11+ and Maven 3.6+.

```sh
mvn spring-boot:run
```

The local H2 database is written under `./data/`. That directory is ignored by Git. This service is for local development; it has **no authentication** or production-grade migrations. Don't expose it to the internet or store real sensitive incident data.

## API

```sh
# Create an incident
curl -i -X POST http://localhost:8080/api/incidents \
  -H 'Content-Type: application/json' \
  -d '{"title":"Unusual login","description":"Multiple failed attempts","severity":"HIGH"}'

# Read, filter and paginate
curl 'http://localhost:8080/api/incidents?severity=HIGH&status=OPEN&page=0&size=20'

# Keyword search over titles and descriptions (combines with the filters)
curl 'http://localhost:8080/api/incidents?q=login&status=OPEN'

# Only incidents created inside a time window (ISO-8601, both ends inclusive)
curl 'http://localhost:8080/api/incidents?created_after=2026-11-01T00:00:00Z&created_before=2026-11-03T23:59:59Z'
curl http://localhost:8080/api/incidents/1

# Triage overview: counts by status and severity
curl http://localhost:8080/api/incidents/summary

# Change status (OPEN, INVESTIGATING, RESOLVED)
curl -i -X PATCH http://localhost:8080/api/incidents/1/status \
  -H 'Content-Type: application/json' -d '{"status":"INVESTIGATING"}'

# Re-triage severity (LOW, MEDIUM, HIGH, CRITICAL)
curl -i -X PATCH http://localhost:8080/api/incidents/1/severity \
  -H 'Content-Type: application/json' -d '{"severity":"CRITICAL"}'

# Timeline: creation, status and severity events, oldest first
curl http://localhost:8080/api/incidents/1/events

# Set (or clear, with null) a resolution deadline
curl -i -X PATCH http://localhost:8080/api/incidents/1/due \
  -H 'Content-Type: application/json' -d '{"dueAt":"2026-12-01T00:00:00Z"}'

# Only open incidents past their deadline
curl 'http://localhost:8080/api/incidents?overdue=true'
```

Severities: `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`. Severity is an assessment label, not lifecycle: `PATCH /{id}/severity` re-triages an incident at any point, including after resolution, and never touches status or timestamps. Invalid payloads, enum values, and page sizes return 400. A payload that fails validation gets `{"error": "Invalid incident payload", "fields": {"title": "..."}}`, naming each bad field; missing IDs return 404; reopening a resolved incident returns 409. Pagination is capped at 100 rows per page. The offset (`page * size`) must not exceed 2,147,483,647, the JPA limit; larger offsets return 400 rather than a server error. The optional `q` parameter is a case-insensitive keyword search over titles and descriptions; it combines with the `status` and `severity` filters and a blank `q` is ignored. The optional `created_after` and `created_before` parameters bound the `createdAt` timestamp (ISO-8601, both ends inclusive); an unparseable value or an inverted window returns 400. Results are sorted by newest timestamp and ID. An incident can carry an optional `dueAt` deadline (ISO-8601), set at creation or later via `PATCH /{id}/due` (`{"dueAt": null}` clears it); an unparseable value returns 400. The `overdue` list parameter keeps only open incidents past their deadline (`overdue=true`) or everything else (`overdue=false`); a resolved incident is never overdue, and an incident with no deadline cannot be overdue either. `GET /api/incidents/{id}/events` returns that incident's timeline, oldest first: a `CREATED` event plus a `STATUS_CHANGED` or `SEVERITY_CHANGED` event (`kind`, `fromValue`, `toValue`, `happenedAt`) for each real transition; repeat PATCHes that change nothing record no event, and a rejected reopen records none either. `GET /api/incidents/summary` returns `{"total", "byStatus", "bySeverity", "averageResolutionSeconds", "overdueOpen"}`; every status and severity appears, with 0 when none match, and `overdueOpen` counts open incidents past their deadline. Resolving an incident stamps `resolvedAt` (a repeat RESOLVED keeps the first timestamp), and the summary's `averageResolutionSeconds` is the mean created-to-resolved time across resolved incidents, or null when none are resolved yet.

## Test

```sh
mvn verify
```

Forty MockMvc integration tests exercise create/read, keyword search (title and description matching, combined filters, no-match and blank keywords), created-window filtering (after/before, inclusive boundaries, combined with keyword search, bad values), severity re-triage (including post-resolution and input validation), input validation (including per-field error messages and the title length limit), malformed JSON and bad filter values, missing records and pagination (including oversized offsets and accepted boundary values), combined filters, newest-first ordering, the summary counts and average resolution time (empty and populated), resolution timestamps, the status lifecycle, the per-incident event timeline (creation, status and severity changes in order, no event on no-op or rejected transitions), and due-date handling (round-trip, set/clear, missing record, unparseable value, the overdue filter's open-only semantics, and the summary's overdue count) against an in-memory H2 database. CI runs the same tests on Java 11 and 17. This is not an AI classifier; severities are supplied by the caller and the API does not automatically assess threats.

## Structure

- `IncidentController`: HTTP routes, request validation, filtering, lifecycle rule.
- `Incident`: persisted incident model and enums.
- `IncidentRepository`: JPA queries for status/severity/keyword/time-window filters.
- `IncidentEvent`: audit-trail row (kind, from/to value, timestamp) per incident.
- `IncidentControllerTest`: full HTTP/database integration tests.

MIT licensed.
