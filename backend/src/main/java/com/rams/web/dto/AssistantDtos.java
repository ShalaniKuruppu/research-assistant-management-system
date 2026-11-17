package com.rams.web.dto;

import com.rams.domain.ProjectStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public final class AssistantDtos {

    private AssistantDtos() {
    }

    public record CreateResearchAssistantRequest(
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Size(max = 254) String email,
            @NotBlank @Size(min = 8, max = 72) String password,
            @Size(max = 40) String phone
    ) {
    }

    public record AssignedProjectSummary(
            Long id,
            String title,
            ProjectStatus status
    ) {
    }

    public record AssistantProfileResponse(
            Long id,
            String name,
            String email,
            String phone,
            String summary,
            String profileImageUrl,
            boolean active,
            List<AssignedProjectSummary> projects
    ) {
    }
}
