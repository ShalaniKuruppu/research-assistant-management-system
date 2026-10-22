package com.rams.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import org.hibernate.annotations.Type;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "task_updates")
public class TaskUpdate extends Timestamped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "update_id")
    private Long id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "updated_by", nullable = false)
    private Long updatedBy;

    @Type(PgEnumType.TaskStatusType.class)
    @Column(nullable = false, columnDefinition = "task_status")
    private TaskStatus status;

    @Column(name = "completion_percentage", nullable = false)
    private short completionPercentage;

    @Column(name = "progress_note", nullable = false, columnDefinition = "text")
    private String progressNote;

    @Column(name = "blocker_note", columnDefinition = "text")
    private String blockerNote;

    public Long getId() {
        return id;
    }

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(Long updatedBy) {
        this.updatedBy = updatedBy;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public short getCompletionPercentage() {
        return completionPercentage;
    }

    public void setCompletionPercentage(short completionPercentage) {
        this.completionPercentage = completionPercentage;
    }

    public String getProgressNote() {
        return progressNote;
    }

    public void setProgressNote(String progressNote) {
        this.progressNote = progressNote;
    }

    public String getBlockerNote() {
        return blockerNote;
    }

    public void setBlockerNote(String blockerNote) {
        this.blockerNote = blockerNote;
    }
}
