package dev.prajit.triage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:triage;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class IncidentControllerTest {
    @Autowired MockMvc mvc;
    @Autowired IncidentRepository repository;
    @BeforeEach void clear() { repository.deleteAll(); }
    private String payload(String severity) {
        return "{\"title\":\"Unusual login\",\"description\":\"Multiple failed attempts\",\"severity\":\"" + severity + "\"}";
    }
    private long create() throws Exception {
        String body = mvc.perform(post("/api/incidents").contentType("application/json").content(payload("HIGH")))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("OPEN"))
            .andReturn().getResponse().getContentAsString();
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(body).get("id").asLong();
    }
    @Test void createAndFetch() throws Exception {
        long id = create();
        mvc.perform(get("/api/incidents/{id}", id)).andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Unusual login"))
            .andExpect(jsonPath("$.severity").value("HIGH"));
    }
    @Test void rejectsInvalidPayload() throws Exception {
        mvc.perform(post("/api/incidents").contentType("application/json")
            .content("{\"title\":\"\",\"description\":\"x\",\"severity\":\"LOW\"}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/incidents").contentType("application/json").content(payload("INVALID")))
            .andExpect(status().isBadRequest());
    }
    @Test void missingAndBadPagination() throws Exception {
        mvc.perform(get("/api/incidents/999999")).andExpect(status().isNotFound());
        mvc.perform(get("/api/incidents?size=101")).andExpect(status().isBadRequest());
    }
    @Test void filterAndPage() throws Exception {
        long id = create();
        mvc.perform(post("/api/incidents").contentType("application/json").content(payload("LOW"))).andExpect(status().isCreated());
        mvc.perform(get("/api/incidents?severity=HIGH&status=OPEN&size=1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].id").value((int) id));
    }
    @Test void updateAndNoReopen() throws Exception {
        long id = create();
        mvc.perform(patch("/api/incidents/{id}/status", id).contentType("application/json")
            .content("{\"status\":\"RESOLVED\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("RESOLVED"));
        mvc.perform(patch("/api/incidents/{id}/status", id).contentType("application/json")
            .content("{\"status\":\"OPEN\"}"))
            .andExpect(status().isConflict());
    }
    @Test void validationErrorsNameTheBadFields() throws Exception {
        mvc.perform(post("/api/incidents").contentType("application/json")
            .content("{\"title\":\"\",\"description\":\"x\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Invalid incident payload"))
            .andExpect(jsonPath("$.fields.title").exists())
            .andExpect(jsonPath("$.fields.severity").exists())
            .andExpect(jsonPath("$.fields.description").doesNotExist());
    }
    @Test void rejectsOverlongTitle() throws Exception {
        String longTitle = "t".repeat(121);
        mvc.perform(post("/api/incidents").contentType("application/json")
            .content("{\"title\":\"" + longTitle + "\",\"description\":\"x\",\"severity\":\"LOW\"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.title").exists());
    }
    @Test void malformedJsonAndBadFiltersAreBadRequests() throws Exception {
        mvc.perform(post("/api/incidents").contentType("application/json").content("{not json"))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/incidents?status=BOGUS")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/incidents?severity=BOGUS")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/incidents?page=-1")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/incidents/abc")).andExpect(status().isBadRequest());
    }
    @Test void statusUpdateOnMissingIncidentIsNotFound() throws Exception {
        mvc.perform(patch("/api/incidents/{id}/status", 999999).contentType("application/json")
            .content("{\"status\":\"INVESTIGATING\"}")).andExpect(status().isNotFound());
        long id = create();
        mvc.perform(patch("/api/incidents/{id}/status", id).contentType("application/json")
            .content("{\"status\":\"BOGUS\"}")).andExpect(status().isBadRequest());
        mvc.perform(patch("/api/incidents/{id}/status", id).contentType("application/json")
            .content("{}")).andExpect(status().isBadRequest());
    }
    @Test void resolvedStaysResolvedOnRepeat() throws Exception {
        long id = create();
        for (int i = 0; i < 2; i++)
            mvc.perform(patch("/api/incidents/{id}/status", id).contentType("application/json")
                .content("{\"status\":\"RESOLVED\"}")).andExpect(status().isOk());
    }
    @Test void newestIncidentsComeFirst() throws Exception {
        long first = create();
        long second = create();
        mvc.perform(get("/api/incidents")).andExpect(status().isOk())
            .andExpect(jsonPath("$.content[0].id").value((int) second))
            .andExpect(jsonPath("$.content[1].id").value((int) first));
    }
    @Test void rejectsPaginationOffsetsBeyondJpaLimit() throws Exception {
        mvc.perform(get("/api/incidents?page=2147483647&size=100"))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/incidents?page=21474837&size=100&status=OPEN"))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/incidents?page=21474837&size=100&severity=HIGH"))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/incidents?page=21474837&size=100&status=OPEN&severity=HIGH"))
            .andExpect(status().isBadRequest());
    }
    @Test void acceptsLargePagesWithinJpaOffsetLimit() throws Exception {
        mvc.perform(get("/api/incidents?page=21474836&size=100"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
        mvc.perform(get("/api/incidents?page=2147483647&size=1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content").isEmpty());
    }
    @Test void summaryOfEmptyStoreIsZeroFilled() throws Exception {
        mvc.perform(get("/api/incidents/summary")).andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(0))
            .andExpect(jsonPath("$.byStatus.OPEN").value(0))
            .andExpect(jsonPath("$.byStatus.INVESTIGATING").value(0))
            .andExpect(jsonPath("$.byStatus.RESOLVED").value(0))
            .andExpect(jsonPath("$.bySeverity.LOW").value(0))
            .andExpect(jsonPath("$.bySeverity.CRITICAL").value(0));
    }
    @Test void resolvingSetsResolvedAtOnce() throws Exception {
        long id = create();
        mvc.perform(get("/api/incidents/{id}", id)).andExpect(status().isOk())
            .andExpect(jsonPath("$.resolvedAt").value(org.hamcrest.Matchers.nullValue()));
        mvc.perform(patch("/api/incidents/{id}/status", id).contentType("application/json")
            .content("{\"status\":\"RESOLVED\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resolvedAt").isNotEmpty());
        // Compare against the stored value: H2 rounds timestamps to micros, so the
        // write response's nanosecond value need not match what later reads return.
        String resolvedAt = repository.findById(id).orElseThrow().resolvedAt.toString();
        mvc.perform(patch("/api/incidents/{id}/status", id).contentType("application/json")
            .content("{\"status\":\"RESOLVED\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resolvedAt").value(resolvedAt));
        java.time.Instant resolved = repository.findById(id).orElseThrow().resolvedAt;
        java.time.Instant created = repository.findById(id).orElseThrow().createdAt;
        org.junit.jupiter.api.Assertions.assertFalse(resolved.isBefore(created));
    }
    @Test void summaryAveragesResolutionTime() throws Exception {
        long first = create();
        long second = create();
        for (long id : new long[]{first, second})
            mvc.perform(patch("/api/incidents/{id}/status", id).contentType("application/json")
                .content("{\"status\":\"RESOLVED\"}")).andExpect(status().isOk());
        double expected = 0;
        for (long id : new long[]{first, second}) {
            Incident incident = repository.findById(id).orElseThrow();
            expected += java.time.Duration.between(incident.createdAt, incident.resolvedAt).toMillis() / 1000.0;
        }
        expected = Math.round(expected / 2 * 10.0) / 10.0;
        mvc.perform(get("/api/incidents/summary")).andExpect(status().isOk())
            .andExpect(jsonPath("$.averageResolutionSeconds").value(expected));
    }
    @Test void summaryWithoutResolutionsHasNullAverage() throws Exception {
        create();
        mvc.perform(get("/api/incidents/summary")).andExpect(status().isOk())
            .andExpect(jsonPath("$.averageResolutionSeconds").value(org.hamcrest.Matchers.nullValue()));
    }
    @Test void summaryCountsByStatusAndSeverity() throws Exception {
        long first = create();
        create();
        mvc.perform(post("/api/incidents").contentType("application/json").content(payload("LOW")))
            .andExpect(status().isCreated());
        mvc.perform(patch("/api/incidents/{id}/status", first).contentType("application/json").content("{\"status\":\"RESOLVED\"}"))
            .andExpect(status().isOk());
        mvc.perform(get("/api/incidents/summary")).andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(3))
            .andExpect(jsonPath("$.byStatus.OPEN").value(2))
            .andExpect(jsonPath("$.byStatus.INVESTIGATING").value(0))
            .andExpect(jsonPath("$.byStatus.RESOLVED").value(1))
            .andExpect(jsonPath("$.bySeverity.HIGH").value(2))
            .andExpect(jsonPath("$.bySeverity.LOW").value(1))
            .andExpect(jsonPath("$.bySeverity.MEDIUM").value(0));
    }
    @Test void searchMatchesTitleAndDescriptionCaseInsensitively() throws Exception {
        long login = create(); // title "Unusual login", description "Multiple failed attempts"
        mvc.perform(post("/api/incidents").contentType("application/json")
            .content("{\"title\":\"Disk full\",\"description\":\"No space left on /var\",\"severity\":\"MEDIUM\"}"))
            .andExpect(status().isCreated());
        mvc.perform(get("/api/incidents?q=LOGIN")).andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].id").value((int) login));
        mvc.perform(get("/api/incidents").param("q", "failed attempts")).andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].id").value((int) login));
        mvc.perform(get("/api/incidents?q=space")).andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].title").value("Disk full"));
    }
    @Test void searchCombinesWithStatusAndSeverity() throws Exception {
        long first = create();
        long second = create();
        mvc.perform(patch("/api/incidents/{id}/status", second).contentType("application/json")
            .content("{\"status\":\"RESOLVED\"}")).andExpect(status().isOk());
        mvc.perform(get("/api/incidents?q=login&status=RESOLVED")).andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].id").value((int) second));
        mvc.perform(get("/api/incidents?q=login&severity=LOW")).andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/incidents?q=login&severity=HIGH&status=OPEN")).andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].id").value((int) first));
    }
    @Test void searchWithoutMatchIsEmptyAndBlankIsNoFilter() throws Exception {
        create();
        mvc.perform(get("/api/incidents?q=ransomware")).andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(0))
            .andExpect(jsonPath("$.content").isEmpty());
        mvc.perform(get("/api/incidents?q=")).andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(1));
    }
}
