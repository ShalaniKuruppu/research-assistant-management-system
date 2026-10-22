package com.rams.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import org.hibernate.annotations.Type;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "report_feedback")
public class ReportFeedback extends Timestamped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "feedback_id")
    private Long id;

    @Column(name = "report_id", nullable = false)
    private Long reportId;

    @Column(name = "supervisor_id", nullable = false)
    private Long supervisorId;

    @Column(name = "feedback_text", nullable = false, columnDefinition = "text")
    private String feedbackText;

    @Type(PgEnumType.FeedbackOutcomeType.class)
    @Column(nullable = false, columnDefinition = "feedback_outcome")
    private FeedbackOutcome outcome;

    public Long getId() {
        return id;
    }

    public Long getReportId() {
        return reportId;
    }

    public void setReportId(Long reportId) {
        this.reportId = reportId;
    }

    public Long getSupervisorId() {
        return supervisorId;
    }

    public void setSupervisorId(Long supervisorId) {
        this.supervisorId = supervisorId;
    }

    public String getFeedbackText() {
        return feedbackText;
    }

    public void setFeedbackText(String feedbackText) {
        this.feedbackText = feedbackText;
    }

    public FeedbackOutcome getOutcome() {
        return outcome;
    }

    public void setOutcome(FeedbackOutcome outcome) {
        this.outcome = outcome;
    }
}
