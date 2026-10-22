package com.rams.service;

import com.rams.domain.Project;
import com.rams.domain.ProjectAssistant;
import com.rams.domain.ProjectStatus;
import com.rams.domain.UserAccount;
import com.rams.domain.UserRole;
import com.rams.repository.ProjectAssistantRepository;
import com.rams.repository.ProjectRepository;
import com.rams.repository.UserRepository;
import com.rams.web.ApiException;
import com.rams.web.Text;
import com.rams.web.dto.ProjectDtos.AssignmentResponse;
import com.rams.web.dto.ProjectDtos.AssignmentResult;
import com.rams.web.dto.ProjectDtos.CreateProjectRequest;
import com.rams.web.dto.ProjectDtos.ProjectResponse;
import com.rams.web.dto.ProjectDtos.UpdateProjectRequest;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class ProjectService {

    private final ProjectRepository projects;
    private final ProjectAssistantRepository assignments;
    private final UserRepository users;
    private final EntityManager entityManager;

    public ProjectService(
            ProjectRepository projects,
            ProjectAssistantRepository assignments,
            UserRepository users,
            EntityManager entityManager
    ) {
        this.projects = projects;
        this.assignments = assignments;
        this.users = users;
        this.entityManager = entityManager;
    }

    @Transactional
    public ProjectResponse create(UserAccount supervisor, CreateProjectRequest request) {
        requireSupervisor(supervisor);
        String title = Text.required(request.title(), 200, "Title");
        requireDateOrder(request.startDate(), request.endDate());
        Project project = new Project();
        project.setSupervisorId(supervisor.getId());
        project.setTitle(title);
        project.setDescription(Text.optional(request.description(), 20000, "Description"));
        project.setObjectives(Text.optional(request.objectives(), 20000, "Objectives"));
        project.setStartDate(request.startDate());
        project.setEndDate(request.endDate());
        project.setStatus(request.status() == null ? ProjectStatus.PLANNED : request.status());
        projects.save(project);
        return toResponse(project, supervisor, List.of());
    }

    public List<ProjectResponse> list(UserAccount user) {
        List<Project> found = user.getRole() == UserRole.SUPERVISOR
                ? projects.findBySupervisorIdOrderByIdAsc(user.getId())
                : projects.findVisibleToAssistant(user.getId());
        return toResponses(found, user.getRole() == UserRole.SUPERVISOR);
    }

    public ProjectResponse get(UserAccount user, Long projectId) {
        Project project = requireVisible(user, projectId);
        boolean includeInactive = isOwner(user, project);
        return toResponses(List.of(project), includeInactive).get(0);
    }

    @Transactional
    public ProjectResponse update(UserAccount user, Long projectId, UpdateProjectRequest request) {
        Project project = requireOwner(user, projectId);
        if (request.title() != null) {
            project.setTitle(Text.required(request.title(), 200, "Title"));
        }
        if (request.description() != null) {
            project.setDescription(Text.optional(request.description(), 20000, "Description"));
        }
        if (request.objectives() != null) {
            project.setObjectives(Text.optional(request.objectives(), 20000, "Objectives"));
        }
        LocalDate start = request.startDate() == null ? project.getStartDate() : request.startDate();
        LocalDate end = request.endDate() == null ? project.getEndDate() : request.endDate();
        requireDateOrder(start, end);
        project.setStartDate(start);
        project.setEndDate(end);
        if (request.status() != null) {
            project.setStatus(request.status());
        }
        entityManager.flush();
        return toResponses(List.of(project), true).get(0);
    }

    @Transactional
    public AssignmentResult assign(UserAccount user, Long projectId, Long assistantId) {
        requireOwner(user, projectId);
        UserAccount assistant = users.findById(assistantId)
                .orElseThrow(() -> ApiException.badRequest("Choose an active research assistant"));
        if (assistant.getRole() != UserRole.RESEARCH_ASSISTANT || !assistant.isActive()) {
            throw ApiException.badRequest("Choose an active research assistant");
        }
        ProjectAssistant existing = assignments.findByProjectIdAndAssistantId(projectId, assistantId).orElse(null);
        if (existing != null) {
            if (existing.isActive()) {
                throw ApiException.conflict("This assistant is already assigned to the project");
            }
            existing.setActive(true);
            return new AssignmentResult(toAssignment(existing, assistant), false);
        }
        ProjectAssistant created = new ProjectAssistant();
        created.setProjectId(projectId);
        created.setAssistantId(assistantId);
        created.setActive(true);
        assignments.save(created);
        return new AssignmentResult(toAssignment(created, assistant), true);
    }

    public List<AssignmentResponse> listAssistants(UserAccount user, Long projectId) {
        Project project = requireVisible(user, projectId);
        return assignmentsFor(List.of(project.getId()), isOwner(user, project));
    }

    @Transactional
    public AssignmentResponse setAssignmentActive(UserAccount user, Long projectId, Long assistantId, boolean active) {
        requireOwner(user, projectId);
        ProjectAssistant assignment = assignments.findByProjectIdAndAssistantId(projectId, assistantId)
                .orElseThrow(() -> ApiException.notFound("Assignment not found"));
        assignment.setActive(active);
        UserAccount assistant = users.findById(assistantId)
                .orElseThrow(() -> ApiException.notFound("Assignment not found"));
        return toAssignment(assignment, assistant);
    }

    public Project requireVisible(UserAccount user, Long projectId) {
        Project project = projects.findById(projectId)
                .orElseThrow(() -> ApiException.notFound("Project not found"));
        if (isOwner(user, project) || assignments.existsByProjectIdAndAssistantIdAndActiveTrue(projectId, user.getId())) {
            return project;
        }
        throw ApiException.notFound("Project not found");
    }

    public Project requireOwner(UserAccount user, Long projectId) {
        Project project = projects.findById(projectId)
                .orElseThrow(() -> ApiException.notFound("Project not found"));
        if (isOwner(user, project)) {
            return project;
        }
        if (assignments.existsByProjectIdAndAssistantIdAndActiveTrue(projectId, user.getId())) {
            throw ApiException.forbidden("Only the project supervisor can do that");
        }
        throw ApiException.notFound("Project not found");
    }

    public void requireActiveAssistant(UserAccount user, Long projectId) {
        if (user.getRole() != UserRole.RESEARCH_ASSISTANT
                || !assignments.existsByProjectIdAndAssistantIdAndActiveTrue(projectId, user.getId())) {
            Project project = projects.findById(projectId).orElse(null);
            if (project != null && isOwner(user, project)) {
                throw ApiException.forbidden("Only an assigned research assistant can do that");
            }
            throw ApiException.notFound("Project not found");
        }
    }

    private List<ProjectResponse> toResponses(List<Project> found, boolean includeInactiveAssistants) {
        if (found.isEmpty()) {
            return List.of();
        }
        Map<Long, UserAccount> people = new HashMap<>();
        users.findAllById(found.stream().map(Project::getSupervisorId).distinct().toList())
                .forEach(user -> people.put(user.getId(), user));
        List<Long> projectIds = found.stream().map(Project::getId).toList();
        List<AssignmentResponse> assignmentResponses = assignmentsFor(projectIds, includeInactiveAssistants);
        Map<Long, List<AssignmentResponse>> byProject = new HashMap<>();
        for (AssignmentResponse assignment : assignmentResponses) {
            byProject.computeIfAbsent(assignment.projectId(), ignored -> new ArrayList<>()).add(assignment);
        }
        return found.stream()
                .map(project -> {
                    UserAccount supervisor = people.get(project.getSupervisorId());
                    String supervisorName = supervisor == null ? null : supervisor.getName();
                    return toResponse(
                            project,
                            supervisorName,
                            byProject.getOrDefault(project.getId(), List.of())
                    );
                })
                .toList();
    }

    private List<AssignmentResponse> assignmentsFor(Collection<Long> projectIds, boolean includeInactive) {
        List<ProjectAssistant> rows = assignments.findByProjectIdInOrderByIdAsc(projectIds).stream()
                .filter(row -> includeInactive || row.isActive())
                .toList();
        Map<Long, UserAccount> people = new HashMap<>();
        users.findAllById(rows.stream().map(ProjectAssistant::getAssistantId).distinct().toList())
                .forEach(user -> people.put(user.getId(), user));
        return rows.stream()
                .map(row -> toAssignment(row, people.get(row.getAssistantId())))
                .toList();
    }

    private static ProjectResponse toResponse(Project project, UserAccount supervisor, List<AssignmentResponse> assistants) {
        return toResponse(project, supervisor.getName(), assistants);
    }

    private static ProjectResponse toResponse(Project project, String supervisorName, List<AssignmentResponse> assistants) {
        return new ProjectResponse(
                project.getId(),
                project.getSupervisorId(),
                supervisorName,
                project.getTitle(),
                project.getDescription(),
                project.getObjectives(),
                project.getStartDate(),
                project.getEndDate(),
                project.getStatus(),
                project.getCreatedAt(),
                project.getUpdatedAt(),
                assistants
        );
    }

    private static AssignmentResponse toAssignment(ProjectAssistant assignment, UserAccount assistant) {
        return new AssignmentResponse(
                assignment.getId(),
                assignment.getProjectId(),
                assignment.getAssistantId(),
                assistant == null ? null : assistant.getName(),
                assistant == null ? null : assistant.getEmail(),
                assignment.isActive(),
                assignment.getAssignedAt()
        );
    }

    private static boolean isOwner(UserAccount user, Project project) {
        return user.getRole() == UserRole.SUPERVISOR && project.getSupervisorId().equals(user.getId());
    }

    private static void requireSupervisor(UserAccount user) {
        if (user.getRole() != UserRole.SUPERVISOR) {
            throw ApiException.forbidden("Only a supervisor can do that");
        }
    }

    static void requireDateOrder(LocalDate start, LocalDate end) {
        if (end.isBefore(start)) {
            throw ApiException.badRequest("The end date must be on or after the start date");
        }
    }
}
