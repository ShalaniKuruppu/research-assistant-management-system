package com.rams.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import org.hibernate.annotations.Type;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "weekly_reports")
@DynamicUpdate
public class WeeklyReport extends Timestamped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "report_id")
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "assistant_id", nullable = false)
    private Long assistantId;

    @Column(name = "week_start_date", nullable = false)
    private LocalDate weekStartDate;

    @Column(name = "week_end_date", nullable = false)
    private LocalDate weekEndDate;

    @Column(name = "work_completed", columnDefinition = "text")
    private String workCompleted;

    @Column(name = "current_tasks", columnDefinition = "text")
    private String currentTasks;

    @Column(columnDefinition = "text")
    private String findings;

    @Column(name = "problems_support", columnDefinition = "text")
    private String problemsSupport;

    @Column(name = "next_week_plan", columnDefinition = "text")
    private String nextWeekPlan;

    @Column(name = "supporting_links", columnDefinition = "text")
    private String supportingLinks;

    @Type(PgEnumType.ReportStatusType.class)
    @Column(nullable = false, columnDefinition = "report_status")
    private ReportStatus status = ReportStatus.DRAFT;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    public Long getId() {
        return id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public Long getAssistantId() {
        return assistantId;
    }

    public void setAssistantId(Long assistantId) {
        this.assistantId = assistantId;
    }

    public LocalDate getWeekStartDate() {
        return weekStartDate;
    }

    public void setWeekStartDate(LocalDate weekStartDate) {
        this.weekStartDate = weekStartDate;
    }

    public LocalDate getWeekEndDate() {
        return weekEndDate;
    }

    public void setWeekEndDate(LocalDate weekEndDate) {
        this.weekEndDate = weekEndDate;
    }

    public String getWorkCompleted() {
        return workCompleted;
    }

    public void setWorkCompleted(String workCompleted) {
        this.workCompleted = workCompleted;
    }

    public String getCurrentTasks() {
        return currentTasks;
    }

    public void setCurrentTasks(String currentTasks) {
        this.currentTasks = currentTasks;
    }

    public String getFindings() {
        return findings;
    }

    public void setFindings(String findings) {
        this.findings = findings;
    }

    public String getProblemsSupport() {
        return problemsSupport;
    }

    public void setProblemsSupport(String problemsSupport) {
        this.problemsSupport = problemsSupport;
    }

    public String getNextWeekPlan() {
        return nextWeekPlan;
    }

    public void setNextWeekPlan(String nextWeekPlan) {
        this.nextWeekPlan = nextWeekPlan;
    }

    public String getSupportingLinks() {
        return supportingLinks;
    }

    public void setSupportingLinks(String supportingLinks) {
        this.supportingLinks = supportingLinks;
    }

    public ReportStatus getStatus() {
        return status;
    }

    public void setStatus(ReportStatus status) {
        this.status = status;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Instant submittedAt) {
        this.submittedAt = submittedAt;
    }
}
