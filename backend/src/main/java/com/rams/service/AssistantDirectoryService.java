package com.rams.service;

import com.rams.domain.Project;
import com.rams.domain.ProjectAssistant;
import com.rams.domain.UserAccount;
import com.rams.domain.UserRole;
import com.rams.repository.ProjectAssistantRepository;
import com.rams.repository.ProjectRepository;
import com.rams.repository.UserRepository;
import com.rams.web.ApiException;
import com.rams.web.dto.AssistantDtos.AssignedProjectSummary;
import com.rams.web.dto.AssistantDtos.AssistantProfileResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class AssistantDirectoryService {

    private final UserRepository users;
    private final ProjectRepository projects;
    private final ProjectAssistantRepository assignments;

    public AssistantDirectoryService(
            UserRepository users,
            ProjectRepository projects,
            ProjectAssistantRepository assignments
    ) {
        this.users = users;
        this.projects = projects;
        this.assignments = assignments;
    }

    public List<AssistantProfileResponse> listForSupervisor(UserAccount viewer) {
        if (viewer.getRole() != UserRole.SUPERVISOR) {
            throw ApiException.forbidden("Only a supervisor can browse research assistants");
        }

        List<UserAccount> assistants = users.findByRoleAndActiveTrueOrderByNameAsc(UserRole.RESEARCH_ASSISTANT);
        List<Project> owned = projects.findBySupervisorIdOrderByIdAsc(viewer.getId());
        Map<Long, Project> ownedById = new HashMap<>();
        for (Project project : owned) {
            ownedById.put(project.getId(), project);
        }

        Map<Long, List<AssignedProjectSummary>> projectsByAssistant = new HashMap<>();
        if (!ownedById.isEmpty()) {
            for (ProjectAssistant assignment : assignments.findByProjectIdInAndActiveTrueOrderByIdAsc(ownedById.keySet())) {
                Project project = ownedById.get(assignment.getProjectId());
                if (project == null) {
                    continue;
                }
                projectsByAssistant
                        .computeIfAbsent(assignment.getAssistantId(), ignored -> new ArrayList<>())
                        .add(new AssignedProjectSummary(project.getId(), project.getTitle(), project.getStatus()));
            }
        }

        List<AssistantProfileResponse> profiles = new ArrayList<>();
        for (UserAccount assistant : assistants) {
            profiles.add(new AssistantProfileResponse(
                    assistant.getId(),
                    assistant.getName(),
                    assistant.getEmail(),
                    assistant.getPhone(),
                    assistant.getSummary(),
                    assistant.getProfileImageUrl(),
                    assistant.isActive(),
                    List.copyOf(projectsByAssistant.getOrDefault(assistant.getId(), List.of()))
            ));
        }
        return profiles;
    }
}
