package com.rams.web.dto;

import com.rams.domain.FeedbackOutcome;
import com.rams.domain.ReportStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;

public final class ReportDtos {

    private ReportDtos() {
    }

    public record CreateReportRequest(
            @NotNull LocalDate weekStartDate,
            LocalDate weekEndDate,
            @Size(max = 20000) String workCompleted,
            @Size(max = 20000) String currentTasks,
            @Size(max = 20000) String findings,
            @Size(max = 20000) String problemsSupport,
            @Size(max = 20000) String nextWeekPlan,
            @Size(max = 20000) String supportingLinks
    ) {
    }

    public record UpdateReportRequest(
            LocalDate weekStartDate,
            LocalDate weekEndDate,
            @Size(max = 20000) String workCompleted,
            @Size(max = 20000) String currentTasks,
            @Size(max = 20000) String findings,
            @Size(max = 20000) String problemsSupport,
            @Size(max = 20000) String nextWeekPlan,
            @Size(max = 20000) String supportingLinks
    ) {
    }

    public record CreateFeedbackRequest(
            @NotBlank @Size(max = 20000) String feedbackText,
            @NotNull FeedbackOutcome outcome
    ) {
    }

    public record ReportResponse(
            Long id,
            Long projectId,
            Long assistantId,
            LocalDate weekStartDate,
            LocalDate weekEndDate,
            String workCompleted,
            String currentTasks,
            String findings,
            String problemsSupport,
            String nextWeekPlan,
            String supportingLinks,
            ReportStatus status,
            FeedbackOutcome latestOutcome,
            Instant submittedAt,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record FeedbackResponse(
            Long id,
            Long reportId,
            Long supervisorId,
            String feedbackText,
            FeedbackOutcome outcome,
            Instant createdAt
    ) {
    }
}
