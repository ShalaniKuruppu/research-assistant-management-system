package com.rams.web.dto;

import com.rams.domain.TaskPriority;
import com.rams.domain.TaskStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;

public final class TaskDtos {

    private TaskDtos() {
    }

    public record CreateTaskRequest(
            @NotNull Long assignedTo,
            @NotBlank @Size(max = 200) String title,
            @Size(max = 20000) String description,
            TaskPriority priority,
            @NotNull LocalDate startDate,
            @NotNull LocalDate dueDate,
            @Size(max = 20000) String expectedDeliverables
    ) {
    }

    public record UpdateTaskRequest(
            Long assignedTo,
            @Size(max = 200) String title,
            @Size(max = 20000) String description,
            TaskPriority priority,
            LocalDate startDate,
            LocalDate dueDate,
            @Size(max = 20000) String expectedDeliverables,
            Boolean active
    ) {
    }

    public record CreateTaskUpdateRequest(
            @NotNull TaskStatus status,
            @NotNull @Min(0) @Max(100) Integer completionPercentage,
            @NotBlank @Size(max = 20000) String progressNote,
            @Size(max = 20000) String blockerNote
    ) {
    }

    public record TaskResponse(
            Long id,
            Long projectId,
            Long assignedTo,
            Long createdBy,
            String title,
            String description,
            TaskPriority priority,
            LocalDate startDate,
            LocalDate dueDate,
            String expectedDeliverables,
            TaskStatus status,
            short completionPercentage,
            boolean active,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record TaskUpdateResponse(
            Long id,
            Long taskId,
            Long updatedBy,
            TaskStatus status,
            short completionPercentage,
            String progressNote,
            String blockerNote,
            Instant createdAt
    ) {
    }

    public record ProgressResponse(TaskUpdateResponse update, TaskResponse task) {
    }
}
