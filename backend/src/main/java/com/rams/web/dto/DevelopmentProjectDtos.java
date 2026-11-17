package com.rams.web.dto;

import com.rams.domain.DevelopmentProjectStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class DevelopmentProjectDtos {

    private DevelopmentProjectDtos() {
    }

    public record CreateDevelopmentProjectRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 20000) String description,
            @Size(max = 20000) String objectives,
            @Size(max = 1000) String repositoryUrl,
            DevelopmentProjectStatus status,
            @Size(max = 20000) String progressNote
    ) {
    }

    public record UpdateDevelopmentProjectRequest(
            @Size(max = 200) String title,
            @Size(max = 20000) String description,
            @Size(max = 20000) String objectives,
            @Size(max = 1000) String repositoryUrl,
            DevelopmentProjectStatus status,
            @Size(max = 20000) String progressNote
    ) {
    }

    public record DevelopmentProjectResponse(
            Long id,
            Long assistantId,
            String assistantName,
            String title,
            String description,
            String objectives,
            String repositoryUrl,
            DevelopmentProjectStatus status,
            String progressNote,
            Instant createdAt,
            Instant updatedAt
    ) {
    }
}
