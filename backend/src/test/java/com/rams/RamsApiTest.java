package com.rams;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class RamsApiTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withCopyFileToContainer(
                    MountableFile.forHostPath(schemaPath().toString()),
                    "/docker-entrypoint-initdb.d/01-schema.sql"
            );

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("rams.jwt.secret", () -> "test-secret-key-must-be-at-least-32-bytes");
    }

    @Test
    void supervisorAndAssistantCompleteTheWeeklyFlow() throws Exception {
        String supervisorToken = tokenOf(register(
                "Ada Supervisor",
                "ada@example.com",
                "SUPERVISOR"
        ));
        long assistantId = userIdOf(register(
                "Grace Assistant",
                "grace@example.com",
                "RESEARCH_ASSISTANT"
        ));
        String assistantToken = login("grace@example.com");
        String outsiderToken = tokenOf(register(
                "Other Supervisor",
                "other@example.com",
                "SUPERVISOR"
        ));

        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("up"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"ada@example.com","password":"wrong-password"}
                                """))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + assistantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(projectJson()))
                .andExpect(status().isForbidden());

        long projectId = idOf(mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(projectJson()))
                .andExpect(status().isCreated())
                .andReturn());

        mockMvc.perform(get("/api/projects/" + projectId)
                        .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/projects/" + projectId + "/assistants")
                        .header("Authorization", "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assistantId\":" + assistantId + "}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/projects/" + projectId + "/assistants")
                        .header("Authorization", "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assistantId\":" + assistantId + "}"))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/projects")
                        .header("Authorization", "Bearer " + assistantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(projectId));

        long taskId = idOf(mockMvc.perform(post("/api/projects/" + projectId + "/tasks")
                        .header("Authorization", "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "assignedTo": %d,
                                  "title": "Read the source papers",
                                  "priority": "HIGH",
                                  "startDate": "2026-10-19",
                                  "dueDate": "2026-10-23"
                                }
                                """.formatted(assistantId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("TODO"))
                .andReturn());

        mockMvc.perform(post("/api/tasks/" + taskId + "/updates")
                        .header("Authorization", "Bearer " + assistantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "BLOCKED",
                                  "completionPercentage": 10,
                                  "progressNote": "Waiting on the archive"
                                }
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/tasks/" + taskId + "/updates")
                        .header("Authorization", "Bearer " + assistantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "IN_PROGRESS",
                                  "completionPercentage": 40,
                                  "progressNote": "Finished the first pass"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.task.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.task.completionPercentage").value(40));

        mockMvc.perform(get("/api/projects/" + projectId + "/tasks")
                        .header("Authorization", "Bearer " + assistantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("IN_PROGRESS"));

        long reportId = idOf(mockMvc.perform(post("/api/projects/" + projectId + "/reports")
                        .header("Authorization", "Bearer " + assistantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"weekStartDate":"2026-10-19","weekEndDate":"2026-10-25"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn());

        mockMvc.perform(post("/api/reports/" + reportId + "/submit")
                        .header("Authorization", "Bearer " + assistantToken))
                .andExpect(status().isBadRequest());

        mockMvc.perform(patch("/api/reports/" + reportId)
                        .header("Authorization", "Bearer " + assistantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workCompleted\":\"Read two papers and logged the gaps\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/reports/" + reportId + "/submit")
                        .header("Authorization", "Bearer " + assistantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));

        mockMvc.perform(post("/api/reports/" + reportId + "/feedback")
                        .header("Authorization", "Bearer " + assistantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"feedbackText":"Looks good","outcome":"APPROVED"}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/reports/" + reportId + "/feedback")
                        .header("Authorization", "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"feedbackText":"Add the citation list","outcome":"NEEDS_REVISION"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/reports/" + reportId)
                        .header("Authorization", "Bearer " + assistantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVIEWED"))
                .andExpect(jsonPath("$.latestOutcome").value("NEEDS_REVISION"));

        mockMvc.perform(patch("/api/reports/" + reportId)
                        .header("Authorization", "Bearer " + assistantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workCompleted\":\"Read two papers and added the citation list\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/reports/" + reportId + "/feedback")
                        .header("Authorization", "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"feedbackText":"Approved","outcome":"APPROVED"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(patch("/api/reports/" + reportId)
                        .header("Authorization", "Bearer " + assistantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"findings\":\"Too late to change\"}"))
                .andExpect(status().isForbidden());
    }

    private MvcResult register(String name, String email, String role) throws Exception {
        return mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","email":"%s","password":"password123","role":"%s"}
                                """.formatted(name, email, role)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();
    }

    private String login(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();
        return JsonPath.read(result.getResponse().getContentAsString(), "$.token");
    }

    private static String tokenOf(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.token");
    }

    private static long idOf(MvcResult result) throws Exception {
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }

    private static long userIdOf(MvcResult result) throws Exception {
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.user.id");
        return id.longValue();
    }

    private static String projectJson() {
        return """
                {
                  "title": "Archive study",
                  "description": "Weekly reading group",
                  "startDate": "2026-10-01",
                  "endDate": "2026-12-01"
                }
                """;
    }

    private static Path schemaPath() {
        Path direct = Path.of("db", "schema.sql");
        if (Files.exists(direct)) {
            return direct.toAbsolutePath();
        }
        Path sibling = Path.of("..", "db", "schema.sql");
        if (Files.exists(sibling)) {
            return sibling.toAbsolutePath().normalize();
        }
        throw new IllegalStateException("db/schema.sql not found from " + Path.of("").toAbsolutePath());
    }
}
