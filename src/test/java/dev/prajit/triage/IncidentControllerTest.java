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
}
