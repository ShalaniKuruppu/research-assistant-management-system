package com.rams.service;

import com.rams.domain.FeedbackOutcome;
import com.rams.domain.Project;
import com.rams.domain.ReportFeedback;
import com.rams.domain.ReportStatus;
import com.rams.domain.UserAccount;
import com.rams.domain.WeeklyReport;
import com.rams.repository.ReportFeedbackRepository;
import com.rams.repository.WeeklyReportRepository;
import com.rams.web.ApiException;
import com.rams.web.Text;
import com.rams.web.dto.ReportDtos.CreateFeedbackRequest;
import com.rams.web.dto.ReportDtos.CreateReportRequest;
import com.rams.web.dto.ReportDtos.FeedbackResponse;
import com.rams.web.dto.ReportDtos.ReportResponse;
import com.rams.web.dto.ReportDtos.UpdateReportRequest;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class ReportService {

    private final WeeklyReportRepository reports;
    private final ReportFeedbackRepository feedback;
    private final ProjectService projects;
    private final EntityManager entityManager;

    public ReportService(
            WeeklyReportRepository reports,
            ReportFeedbackRepository feedback,
            ProjectService projects,
            EntityManager entityManager
    ) {
        this.reports = reports;
        this.feedback = feedback;
        this.projects = projects;
        this.entityManager = entityManager;
    }

    @Transactional
    public ReportResponse create(UserAccount user, Long projectId, CreateReportRequest request) {
        projects.requireActiveAssistant(user, projectId);
        LocalDate weekEnd = request.weekEndDate() == null
                ? request.weekStartDate().plusDays(6)
                : request.weekEndDate();
        requireWeek(request.weekStartDate(), weekEnd);
        WeeklyReport report = new WeeklyReport();
        report.setProjectId(projectId);
        report.setAssistantId(user.getId());
        report.setWeekStartDate(request.weekStartDate());
        report.setWeekEndDate(weekEnd);
        applyNarrative(report, request.workCompleted(), request.currentTasks(), request.findings(),
                request.problemsSupport(), request.nextWeekPlan(), request.supportingLinks(), true);
        report.setStatus(ReportStatus.DRAFT);
        reports.save(report);
        return toResponse(report, null);
    }

    public List<ReportResponse> list(UserAccount user, Long projectId) {
        Project project = projects.requireVisible(user, projectId);
        List<WeeklyReport> found = project.getSupervisorId().equals(user.getId())
                ? reports.findByProjectIdOrderByWeekStartDateDescIdDesc(projectId)
                : reports.findByProjectIdAndAssistantIdOrderByWeekStartDateDescIdDesc(projectId, user.getId());
        return found.stream().map(report -> toResponse(report, latestOutcome(report.getId()))).toList();
    }

    public ReportResponse get(UserAccount user, Long reportId) {
        WeeklyReport report = requireVisibleReport(user, reportId);
        return toResponse(report, latestOutcome(report.getId()));
    }

    @Transactional
    public ReportResponse update(UserAccount user, Long reportId, UpdateReportRequest request) {
        WeeklyReport report = requireAuthor(user, reportId);
        FeedbackOutcome latest = latestOutcome(report.getId());
        boolean draft = report.getStatus() == ReportStatus.DRAFT;
        boolean needsRevision = report.getStatus() == ReportStatus.REVIEWED && latest == FeedbackOutcome.NEEDS_REVISION;
        if (!draft && !needsRevision) {
            throw ApiException.forbidden("This report can no longer be edited");
        }
        if (draft && (request.weekStartDate() != null || request.weekEndDate() != null)) {
            LocalDate start = request.weekStartDate() == null ? report.getWeekStartDate() : request.weekStartDate();
            LocalDate end = request.weekEndDate() == null ? report.getWeekEndDate() : request.weekEndDate();
            requireWeek(start, end);
            report.setWeekStartDate(start);
            report.setWeekEndDate(end);
        }
        if (!draft && (request.weekStartDate() != null || request.weekEndDate() != null)) {
            throw ApiException.badRequest("The report week cannot change after it is submitted");
        }
        applyNarrative(
                report,
                request.workCompleted(),
                request.currentTasks(),
                request.findings(),
                request.problemsSupport(),
                request.nextWeekPlan(),
                request.supportingLinks(),
                false
        );
        if (report.getStatus() != ReportStatus.DRAFT && Text.trimToNull(report.getWorkCompleted()) == null) {
            throw ApiException.badRequest("Work completed is required once a report has been submitted");
        }
        return toResponse(report, latest);
    }

    @Transactional
    public ReportResponse submit(UserAccount user, Long reportId) {
        WeeklyReport report = requireAuthor(user, reportId);
        if (report.getStatus() != ReportStatus.DRAFT) {
            throw ApiException.conflict("This report has already been submitted");
        }
        if (Text.trimToNull(report.getWorkCompleted()) == null) {
            throw ApiException.badRequest("Work completed is required before a report can be submitted");
        }
        report.setStatus(ReportStatus.SUBMITTED);
        report.setSubmittedAt(Instant.now());
        return toResponse(report, null);
    }

    @Transactional
    public FeedbackResponse addFeedback(UserAccount user, Long reportId, CreateFeedbackRequest request) {
        WeeklyReport report = reports.findById(reportId)
                .orElseThrow(() -> ApiException.notFound("Weekly report not found"));
        projects.requireOwner(user, report.getProjectId());
        if (report.getStatus() == ReportStatus.DRAFT) {
            throw ApiException.badRequest("Feedback cannot be written on a draft weekly report");
        }
        ReportFeedback row = new ReportFeedback();
        row.setReportId(report.getId());
        row.setSupervisorId(user.getId());
        row.setFeedbackText(Text.required(request.feedbackText(), 20000, "Feedback"));
        row.setOutcome(request.outcome());
        feedback.save(row);
        entityManager.flush();
        entityManager.refresh(report);
        return toFeedback(row);
    }

    public List<FeedbackResponse> listFeedback(UserAccount user, Long reportId) {
        WeeklyReport report = requireVisibleReport(user, reportId);
        return feedback.findByReportIdOrderByCreatedAtAscIdAsc(report.getId()).stream()
                .map(ReportService::toFeedback)
                .toList();
    }

    private WeeklyReport requireVisibleReport(UserAccount user, Long reportId) {
        WeeklyReport report = reports.findById(reportId)
                .orElseThrow(() -> ApiException.notFound("Weekly report not found"));
        Project project = projects.requireVisible(user, report.getProjectId());
        if (project.getSupervisorId().equals(user.getId()) || report.getAssistantId().equals(user.getId())) {
            return report;
        }
        throw ApiException.notFound("Weekly report not found");
    }

    private WeeklyReport requireAuthor(UserAccount user, Long reportId) {
        WeeklyReport report = reports.findById(reportId)
                .orElseThrow(() -> ApiException.notFound("Weekly report not found"));
        if (!report.getAssistantId().equals(user.getId())) {
            projects.requireVisible(user, report.getProjectId());
            throw ApiException.forbidden("Only the assistant who wrote the report can do that");
        }
        projects.requireActiveAssistant(user, report.getProjectId());
        return report;
    }

    private FeedbackOutcome latestOutcome(Long reportId) {
        return feedback.findFirstByReportIdOrderByCreatedAtDescIdDesc(reportId)
                .map(ReportFeedback::getOutcome)
                .orElse(null);
    }

    private static void applyNarrative(
            WeeklyReport report,
            String workCompleted,
            String currentTasks,
            String findings,
            String problemsSupport,
            String nextWeekPlan,
            String supportingLinks,
            boolean applyNulls
    ) {
        if (applyNulls || workCompleted != null) {
            report.setWorkCompleted(Text.optional(workCompleted, 20000, "Work completed"));
        }
        if (applyNulls || currentTasks != null) {
            report.setCurrentTasks(Text.optional(currentTasks, 20000, "Current tasks"));
        }
        if (applyNulls || findings != null) {
            report.setFindings(Text.optional(findings, 20000, "Findings"));
        }
        if (applyNulls || problemsSupport != null) {
            report.setProblemsSupport(Text.optional(problemsSupport, 20000, "Problems and support"));
        }
        if (applyNulls || nextWeekPlan != null) {
            report.setNextWeekPlan(Text.optional(nextWeekPlan, 20000, "Next week plan"));
        }
        if (applyNulls || supportingLinks != null) {
            report.setSupportingLinks(Text.optional(supportingLinks, 20000, "Supporting links"));
        }
    }

    private static void requireWeek(LocalDate start, LocalDate end) {
        long days = ChronoUnit.DAYS.between(start, end);
        if (days < 0 || days > 6) {
            throw ApiException.badRequest("The report week must be 7 days or fewer, and the end must not come first");
        }
    }

    private static ReportResponse toResponse(WeeklyReport report, FeedbackOutcome latestOutcome) {
        return new ReportResponse(
                report.getId(),
                report.getProjectId(),
                report.getAssistantId(),
                report.getWeekStartDate(),
                report.getWeekEndDate(),
                report.getWorkCompleted(),
                report.getCurrentTasks(),
                report.getFindings(),
                report.getProblemsSupport(),
                report.getNextWeekPlan(),
                report.getSupportingLinks(),
                report.getStatus(),
                latestOutcome,
                report.getSubmittedAt(),
                report.getCreatedAt(),
                report.getUpdatedAt()
        );
    }

    private static FeedbackResponse toFeedback(ReportFeedback row) {
        return new FeedbackResponse(
                row.getId(),
                row.getReportId(),
                row.getSupervisorId(),
                row.getFeedbackText(),
                row.getOutcome(),
                row.getCreatedAt()
        );
    }
}
