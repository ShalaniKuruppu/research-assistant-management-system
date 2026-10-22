package com.rams.service;

import com.rams.domain.FeedbackOutcome;
import com.rams.domain.ProjectStatus;
import com.rams.domain.ReportFeedback;
import com.rams.domain.ReportStatus;
import com.rams.domain.Task;
import com.rams.domain.TaskStatus;
import com.rams.domain.UserAccount;
import com.rams.domain.UserRole;
import com.rams.domain.WeeklyReport;
import com.rams.repository.ReportFeedbackRepository;
import com.rams.repository.TaskRepository;
import com.rams.repository.UserRepository;
import com.rams.repository.WeeklyReportRepository;
import com.rams.web.dto.DashboardDtos.AttentionItem;
import com.rams.web.dto.DashboardDtos.AttentionKind;
import com.rams.web.dto.DashboardDtos.DashboardResponse;
import com.rams.web.dto.DashboardDtos.ProjectSummary;
import com.rams.web.dto.ProjectDtos.ProjectResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class DashboardService {

    private static final List<AttentionKind> ATTENTION_ORDER = List.of(
            AttentionKind.REVIEW,
            AttentionKind.REVISION,
            AttentionKind.BLOCKED,
            AttentionKind.OVERDUE,
            AttentionKind.DRAFT,
            AttentionKind.MISSING_REPORT
    );

    private final ProjectService projects;
    private final TaskRepository tasks;
    private final WeeklyReportRepository reports;
    private final ReportFeedbackRepository feedback;
    private final UserRepository users;

    public DashboardService(
            ProjectService projects,
            TaskRepository tasks,
            WeeklyReportRepository reports,
            ReportFeedbackRepository feedback,
            UserRepository users
    ) {
        this.projects = projects;
        this.tasks = tasks;
        this.reports = reports;
        this.feedback = feedback;
        this.users = users;
    }

    public DashboardResponse summarize(UserAccount user) {
        List<ProjectResponse> visible = projects.list(user);
        if (visible.isEmpty()) {
            return new DashboardResponse(0, 0, 0, 0, 0, 0, 0, List.of(), List.of());
        }
        List<Long> projectIds = visible.stream().map(ProjectResponse::id).toList();
        boolean supervisor = user.getRole() == UserRole.SUPERVISOR;
        List<Task> taskRows = supervisor
                ? tasks.findByProjectIdIn(projectIds)
                : tasks.findByProjectIdInAndAssignedTo(projectIds, user.getId());
        List<WeeklyReport> reportRows = supervisor
                ? reports.findByProjectIdIn(projectIds)
                : reports.findByProjectIdInAndAssistantId(projectIds, user.getId());
        Map<Long, FeedbackOutcome> outcomes = latestOutcomes(reportRows);
        Map<Long, String> names = namesFor(taskRows, reportRows);
        LocalDate today = LocalDate.now();

        Map<Long, List<Task>> tasksByProject = new HashMap<>();
        for (Task task : taskRows) {
            tasksByProject.computeIfAbsent(task.getProjectId(), ignored -> new ArrayList<>()).add(task);
        }
        Map<Long, List<WeeklyReport>> reportsByProject = new HashMap<>();
        for (WeeklyReport report : reportRows) {
            reportsByProject.computeIfAbsent(report.getProjectId(), ignored -> new ArrayList<>()).add(report);
        }

        List<ProjectSummary> summaries = new ArrayList<>();
        List<AttentionItem> attention = new ArrayList<>();
        int openTaskCount = 0;
        int blockedTaskCount = 0;
        int overdueTaskCount = 0;
        int submittedReportCount = 0;
        int draftCount = 0;
        int revisionCount = 0;

        for (ProjectResponse project : visible) {
            List<Task> projectTasks = tasksByProject.getOrDefault(project.id(), List.of());
            List<WeeklyReport> projectReports = reportsByProject.getOrDefault(project.id(), List.of());
            int open = 0;
            int blocked = 0;
            int completionTotal = 0;
            int activeCount = 0;
            for (Task task : projectTasks) {
                if (!task.isActive()) {
                    continue;
                }
                activeCount++;
                completionTotal += task.getCompletionPercentage();
                boolean openTask = task.getStatus() != TaskStatus.DONE && task.getStatus() != TaskStatus.CANCELLED;
                if (openTask) {
                    open++;
                }
                if (task.getStatus() == TaskStatus.BLOCKED) {
                    blocked++;
                    attention.add(taskItem(AttentionKind.BLOCKED, project, task, names, supervisor));
                }
                if (openTask && task.getDueDate().isBefore(today)) {
                    overdueTaskCount++;
                    attention.add(taskItem(AttentionKind.OVERDUE, project, task, names, supervisor));
                }
            }
            openTaskCount += open;
            blockedTaskCount += blocked;

            for (WeeklyReport report : projectReports) {
                FeedbackOutcome outcome = outcomes.get(report.getId());
                if (report.getStatus() == ReportStatus.SUBMITTED) {
                    submittedReportCount++;
                    if (supervisor) {
                        attention.add(reportItem(AttentionKind.REVIEW, project, report, names));
                    }
                } else if (report.getStatus() == ReportStatus.DRAFT) {
                    draftCount++;
                    if (!supervisor) {
                        attention.add(reportItem(AttentionKind.DRAFT, project, report, names));
                    }
                }
                if (outcome == FeedbackOutcome.NEEDS_REVISION) {
                    revisionCount++;
                    if (!supervisor) {
                        attention.add(reportItem(AttentionKind.REVISION, project, report, names));
                    }
                }
            }
            if (project.status() == ProjectStatus.ACTIVE) {
                attention.addAll(missingReports(project, projectReports, names, supervisor, today));
            }
            int assistants = (int) project.assistants().stream().filter(assistant -> assistant.active()).count();
            int average = activeCount == 0 ? 0 : Math.round(completionTotal / (float) activeCount);
            summaries.add(new ProjectSummary(
                    project.id(),
                    project.title(),
                    project.status(),
                    project.startDate(),
                    project.endDate(),
                    project.supervisorName(),
                    assistants,
                    open,
                    blocked,
                    average
            ));
        }

        attention.sort(Comparator
                .comparingInt((AttentionItem item) -> ATTENTION_ORDER.indexOf(item.kind()))
                .thenComparing(AttentionItem::projectId)
                .thenComparing(AttentionItem::title));

        return new DashboardResponse(
                summaries.size(),
                openTaskCount,
                blockedTaskCount,
                overdueTaskCount,
                submittedReportCount,
                draftCount,
                revisionCount,
                List.copyOf(summaries),
                List.copyOf(attention)
        );
    }

    private Map<Long, FeedbackOutcome> latestOutcomes(List<WeeklyReport> reportRows) {
        Map<Long, FeedbackOutcome> outcomes = new HashMap<>();
        if (reportRows.isEmpty()) {
            return outcomes;
        }
        List<Long> reportIds = reportRows.stream().map(WeeklyReport::getId).toList();
        for (ReportFeedback row : feedback.findByReportIdInOrderByCreatedAtAscIdAsc(reportIds)) {
            outcomes.put(row.getReportId(), row.getOutcome());
        }
        return outcomes;
    }

    private Map<Long, String> namesFor(List<Task> taskRows, List<WeeklyReport> reportRows) {
        Set<Long> ids = new HashSet<>();
        taskRows.forEach(task -> ids.add(task.getAssignedTo()));
        reportRows.forEach(report -> ids.add(report.getAssistantId()));
        Map<Long, String> names = new HashMap<>();
        if (ids.isEmpty()) {
            return names;
        }
        users.findAllById(ids).forEach(person -> names.put(person.getId(), person.getName()));
        return names;
    }

    private List<AttentionItem> missingReports(
            ProjectResponse project,
            List<WeeklyReport> projectReports,
            Map<Long, String> names,
            boolean supervisor,
            LocalDate today
    ) {
        List<AttentionItem> items = new ArrayList<>();
        if (supervisor) {
            project.assistants().stream()
                    .filter(assistant -> assistant.active())
                    .filter(assistant -> !coversToday(projectReports, assistant.assistantId(), today))
                    .forEach(assistant -> {
                        String name = assistant.name() == null ? "An assistant" : assistant.name();
                        items.add(new AttentionItem(
                                AttentionKind.MISSING_REPORT,
                                project.id(),
                                project.title(),
                                null,
                                null,
                                name + " has no report this week",
                                "No report covers today"
                        ));
                    });
            return items;
        }
        if (!coversToday(projectReports, null, today)) {
            items.add(new AttentionItem(
                    AttentionKind.MISSING_REPORT,
                    project.id(),
                    project.title(),
                    null,
                    null,
                    "File this week's report",
                    "No report covers today"
            ));
        }
        return items;
    }

    private static boolean coversToday(List<WeeklyReport> projectReports, Long assistantId, LocalDate today) {
        return projectReports.stream()
                .filter(report -> assistantId == null || assistantId.equals(report.getAssistantId()))
                .anyMatch(report -> !report.getWeekStartDate().isAfter(today) && !report.getWeekEndDate().isBefore(today));
    }

    private static AttentionItem taskItem(
            AttentionKind kind,
            ProjectResponse project,
            Task task,
            Map<Long, String> names,
            boolean supervisor
    ) {
        String detail = "Due " + task.getDueDate();
        if (supervisor) {
            detail = detail + " · " + names.getOrDefault(task.getAssignedTo(), "Assistant");
        }
        return new AttentionItem(kind, project.id(), project.title(), task.getId(), null, task.getTitle(), detail);
    }

    private static AttentionItem reportItem(
            AttentionKind kind,
            ProjectResponse project,
            WeeklyReport report,
            Map<Long, String> names
    ) {
        String week = report.getWeekStartDate() + " to " + report.getWeekEndDate();
        String title = kind == AttentionKind.REVIEW
                ? names.getOrDefault(report.getAssistantId(), "Assistant") + "'s weekly report"
                : "Weekly report";
        return new AttentionItem(kind, project.id(), project.title(), null, report.getId(), title, week);
    }
}
