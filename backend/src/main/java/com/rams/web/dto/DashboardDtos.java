package com.rams.web.dto;

import com.rams.domain.ProjectStatus;

import java.time.LocalDate;
import java.util.List;

public final class DashboardDtos {

    private DashboardDtos() {
    }

    public enum AttentionKind {
        REVIEW,
        REVISION,
        BLOCKED,
        OVERDUE,
        DRAFT,
        MISSING_REPORT
    }

    public record DashboardResponse(
            int projectCount,
            int openTaskCount,
            int blockedTaskCount,
            int overdueTaskCount,
            int submittedReportCount,
            int draftCount,
            int revisionCount,
            List<ProjectSummary> projects,
            List<AttentionItem> attention
    ) {
    }

    public record ProjectSummary(
            Long id,
            String title,
            ProjectStatus status,
            LocalDate startDate,
            LocalDate endDate,
            String supervisorName,
            int assistantCount,
            int openTaskCount,
            int blockedTaskCount,
            int averageCompletion
    ) {
    }

    public record AttentionItem(
            AttentionKind kind,
            Long projectId,
            String projectTitle,
            Long taskId,
            Long reportId,
            String title,
            String detail
    ) {
    }
}
