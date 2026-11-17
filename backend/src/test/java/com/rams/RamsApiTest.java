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

import org.springframework.mock.web.MockMultipartFile;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
        MvcResult supervisorRegistration = register(
                "Ada Supervisor",
                "ada@example.com",
                "SUPERVISOR"
        );
        String supervisorToken = tokenOf(supervisorRegistration);
        long supervisorId = userIdOf(supervisorRegistration);
        long assistantId = userIdOf(register(
                "Grace Assistant",
                "grace@example.com",
                "RESEARCH_ASSISTANT"
        ));
        String assistantToken = login("grace@example.com");
        long peerAssistantId = userIdOf(register(
                "Helen Assistant",
                "helen@example.com",
                "RESEARCH_ASSISTANT"
        ));
        String peerAssistantToken = login("helen@example.com");
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
                        .header("Authorization", "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(projectJson(supervisorId)))
                .andExpect(status().isForbidden());

        long projectId = idOf(mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + assistantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(projectJson(supervisorId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdBy").value(assistantId))
                .andExpect(jsonPath("$.assistants[0].assistantId").value(assistantId))
                .andReturn());

        mockMvc.perform(get("/api/projects/" + projectId)
                        .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/projects/" + projectId + "/assistants")
                        .header("Authorization", "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assistantId\":" + peerAssistantId + "}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/projects/" + projectId + "/assistants")
                        .header("Authorization", "Bearer " + peerAssistantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assistantId\":" + peerAssistantId + "}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/projects/" + projectId + "/assistants")
                        .header("Authorization", "Bearer " + assistantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assistantId\":" + peerAssistantId + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.assistantId").value(peerAssistantId));

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

        mockMvc.perform(get("/api/dashboard"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/dashboard")
                        .header("Authorization", "Bearer " + assistantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectCount").value(1))
                .andExpect(jsonPath("$.openTaskCount").value(1))
                .andExpect(jsonPath("$.blockedTaskCount").value(0))
                .andExpect(jsonPath("$.revisionCount").value(1))
                .andExpect(jsonPath("$.projects[0].title").value("Archive study"))
                .andExpect(jsonPath(
                        "$.attention[?(@.kind == 'REVISION')]",
                        org.hamcrest.Matchers.hasSize(1)
                ));

        mockMvc.perform(get("/api/dashboard")
                        .header("Authorization", "Bearer " + supervisorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectCount").value(1))
                .andExpect(jsonPath("$.openTaskCount").value(1))
                .andExpect(jsonPath("$.submittedReportCount").value(0));

        mockMvc.perform(get("/api/dashboard")
                        .header("Authorization", "Bearer " + outsiderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.projectCount").value(0))
                .andExpect(jsonPath("$.attention", org.hamcrest.Matchers.hasSize(0)));

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

    @Test
    void supervisorCanAddResearchAssistants() throws Exception {
        String supervisorToken = tokenOf(register(
                "Ada Supervisor",
                "ada-create@example.com",
                "SUPERVISOR"
        ));
        String assistantToken = tokenOf(register(
                "Existing Assistant",
                "existing@example.com",
                "RESEARCH_ASSISTANT"
        ));

        mockMvc.perform(post("/api/research-assistants")
                        .header("Authorization", "Bearer " + assistantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Blocked Create",
                                  "email": "blocked@example.com",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/research-assistants")
                        .header("Authorization", "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "New Assistant",
                                  "email": "new-assistant@example.com",
                                  "password": "password123",
                                  "phone": "+1 555 0100"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("New Assistant"))
                .andExpect(jsonPath("$.email").value("new-assistant@example.com"))
                .andExpect(jsonPath("$.phone").value("+1 555 0100"))
                .andExpect(jsonPath("$.active").value(true));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"new-assistant@example.com","password":"password123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("RESEARCH_ASSISTANT"));

        mockMvc.perform(get("/api/research-assistants")
                        .header("Authorization", "Bearer " + supervisorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.email=='new-assistant@example.com')]").exists());
    }

    @Test
    void assistantCanUploadReimbursementReceipts() throws Exception {
        MvcResult supervisorRegistration = register(
                "Reimb Supervisor",
                "reimb-supervisor@example.com",
                "SUPERVISOR"
        );
        String supervisorToken = tokenOf(supervisorRegistration);
        long supervisorId = userIdOf(supervisorRegistration);
        String assistantToken = tokenOf(register(
                "Reimb Assistant",
                "reimb-assistant@example.com",
                "RESEARCH_ASSISTANT"
        ));

        long projectId = idOf(mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + assistantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(projectJson(supervisorId)))
                .andExpect(status().isCreated())
                .andReturn());

        long claimId = idOf(mockMvc.perform(post("/api/projects/" + projectId + "/reimbursements")
                        .header("Authorization", "Bearer " + assistantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Conference travel",
                                  "description": "Train ticket",
                                  "amount": 1250.50,
                                  "currency": "LKR",
                                  "expenseDate": "2026-11-10"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn());

        mockMvc.perform(post("/api/reimbursements/" + claimId + "/submit")
                        .header("Authorization", "Bearer " + assistantToken))
                .andExpect(status().isBadRequest());

        MockMultipartFile receipt = new MockMultipartFile(
                "file",
                "ticket.pdf",
                "application/pdf",
                "%PDF-1.4 reimbursement".getBytes()
        );
        mockMvc.perform(multipart("/api/reimbursements/" + claimId + "/receipts")
                        .file(receipt)
                        .header("Authorization", "Bearer " + assistantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.receipts[0].originalFilename").value("ticket.pdf"));

        mockMvc.perform(post("/api/reimbursements/" + claimId + "/submit")
                        .header("Authorization", "Bearer " + assistantToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));

        mockMvc.perform(get("/api/dashboard")
                        .header("Authorization", "Bearer " + supervisorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reimbursementCount").value(1))
                .andExpect(jsonPath("$.attention[?(@.kind=='REIMBURSEMENT_REVIEW')]").exists());

        mockMvc.perform(post("/api/reimbursements/" + claimId + "/review")
                        .header("Authorization", "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"APPROVED","reviewerNote":"Looks good"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));
    }

    @Test
    void assistantUpdatesDevelopmentProjectsVisibleToSupervisor() throws Exception {
        String supervisorToken = tokenOf(register(
                "Dev Supervisor",
                "dev-supervisor@example.com",
                "SUPERVISOR"
        ));
        String assistantToken = tokenOf(register(
                "Dev Assistant",
                "dev-assistant@example.com",
                "RESEARCH_ASSISTANT"
        ));

        mockMvc.perform(post("/api/development-projects")
                        .header("Authorization", "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Supervisor cannot create","status":"PLANNED"}
                                """))
                .andExpect(status().isForbidden());

        long developmentId = idOf(mockMvc.perform(post("/api/development-projects")
                        .header("Authorization", "Bearer " + assistantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Annotation UI",
                                  "description": "Prototype labeling tool",
                                  "status": "IN_PROGRESS",
                                  "progressNote": "Scaffolded the React app"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Annotation UI"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andReturn());

        mockMvc.perform(patch("/api/development-projects/" + developmentId)
                        .header("Authorization", "Bearer " + assistantToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "progressNote": "Added CSV import",
                                  "repositoryUrl": "https://github.com/example/annotation-ui"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.progressNote").value("Added CSV import"))
                .andExpect(jsonPath("$.repositoryUrl").value("https://github.com/example/annotation-ui"));

        mockMvc.perform(patch("/api/development-projects/" + developmentId)
                        .header("Authorization", "Bearer " + supervisorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"progressNote":"Supervisor should not edit"}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/development-projects")
                        .header("Authorization", "Bearer " + supervisorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(developmentId))
                .andExpect(jsonPath("$[0].assistantName").value("Dev Assistant"));
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

    private static String projectJson(long supervisorId) {
        return """
                {
                  "title": "Archive study",
                  "description": "Weekly reading group",
                  "startDate": "2026-10-01",
                  "endDate": "2026-12-01",
                  "supervisorId": %d
                }
                """.formatted(supervisorId);
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
