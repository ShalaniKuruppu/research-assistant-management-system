package com.rams.web.dto;

import com.rams.domain.ProjectStatus;

import java.util.List;

public final class AssistantDtos {

    private AssistantDtos() {
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
