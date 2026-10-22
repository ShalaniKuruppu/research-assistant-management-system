package com.rams.web.dto;

import com.rams.domain.ProjectStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ProjectDtos {

    private ProjectDtos() {
    }

    public record CreateProjectRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 20000) String description,
            @Size(max = 20000) String objectives,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            ProjectStatus status
    ) {
    }

    public record UpdateProjectRequest(
            @Size(max = 200) String title,
            @Size(max = 20000) String description,
            @Size(max = 20000) String objectives,
            LocalDate startDate,
            LocalDate endDate,
            ProjectStatus status
    ) {
    }

    public record AssignAssistantRequest(@NotNull Long assistantId) {
    }

    public record UpdateAssignmentRequest(@NotNull Boolean active) {
    }

    public record AssignmentResponse(
            Long id,
            Long projectId,
            Long assistantId,
            String name,
            String email,
            boolean active,
            Instant assignedAt
    ) {
    }

    public record ProjectResponse(
            Long id,
            Long supervisorId,
            String supervisorName,
            String title,
            String description,
            String objectives,
            LocalDate startDate,
            LocalDate endDate,
            ProjectStatus status,
            Instant createdAt,
            Instant updatedAt,
            List<AssignmentResponse> assistants
    ) {
    }

    public record AssignmentResult(AssignmentResponse assignment, boolean created) {
    }
}
