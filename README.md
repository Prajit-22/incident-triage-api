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
curl http://localhost:8080/api/incidents/1

# Change status (OPEN, INVESTIGATING, RESOLVED)
curl -i -X PATCH http://localhost:8080/api/incidents/1/status \
  -H 'Content-Type: application/json' -d '{"status":"INVESTIGATING"}'
```

Severities: `LOW`, `MEDIUM`, `HIGH`, `CRITICAL`. Invalid payloads, enum values, and page sizes return 400; missing IDs return 404; reopening a resolved incident returns 409. Pagination is capped at 100 rows per page. Results are sorted by newest timestamp and ID.

## Test

```sh
mvn verify
```

Five MockMvc integration tests exercise create/read, input validation, missing records and pagination, combined filters, and the status lifecycle against an in-memory H2 database. CI runs the same tests on Java 11 and 17. This is not an AI classifier; severities are supplied by the caller and the API does not automatically assess threats.

## Structure

- `IncidentController`: HTTP routes, request validation, filtering, lifecycle rule.
- `Incident`: persisted incident model and enums.
- `IncidentRepository`: JPA queries for status/severity filters.
- `IncidentControllerTest`: full HTTP/database integration tests.

MIT licensed.
