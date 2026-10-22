package com.rams.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import org.hibernate.annotations.Type;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicUpdate;

import java.time.LocalDate;

@Entity
@Table(name = "tasks")
@DynamicUpdate
public class Task extends Timestamped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "task_id")
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "assigned_to", nullable = false)
    private Long assignedTo;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Type(PgEnumType.TaskPriorityType.class)
    @Column(nullable = false, columnDefinition = "task_priority")
    private TaskPriority priority = TaskPriority.MEDIUM;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "expected_deliverables", columnDefinition = "text")
    private String expectedDeliverables;

    @Type(PgEnumType.TaskStatusType.class)
    @Column(nullable = false, columnDefinition = "task_status")
    private TaskStatus status = TaskStatus.TODO;

    @Column(name = "completion_percentage", nullable = false)
    private short completionPercentage;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    public Long getId() {
        return id;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public Long getAssignedTo() {
        return assignedTo;
    }

    public void setAssignedTo(Long assignedTo) {
        this.assignedTo = assignedTo;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public TaskPriority getPriority() {
        return priority;
    }

    public void setPriority(TaskPriority priority) {
        this.priority = priority;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public String getExpectedDeliverables() {
        return expectedDeliverables;
    }

    public void setExpectedDeliverables(String expectedDeliverables) {
        this.expectedDeliverables = expectedDeliverables;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public short getCompletionPercentage() {
        return completionPercentage;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
