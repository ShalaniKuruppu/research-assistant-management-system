package com.rams.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.Type;

@Entity
@Table(name = "development_projects")
@DynamicUpdate
public class DevelopmentProject extends Timestamped {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "development_project_id")
    private Long id;

    @Column(name = "assistant_id", nullable = false)
    private Long assistantId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Column(columnDefinition = "text")
    private String objectives;

    @Column(name = "repository_url", length = 1000)
    private String repositoryUrl;

    @Type(PgEnumType.DevelopmentProjectStatusType.class)
    @Column(nullable = false, columnDefinition = "development_project_status")
    private DevelopmentProjectStatus status = DevelopmentProjectStatus.PLANNED;

    @Column(name = "progress_note", columnDefinition = "text")
    private String progressNote;

    public Long getId() {
        return id;
    }

    public Long getAssistantId() {
        return assistantId;
    }

    public void setAssistantId(Long assistantId) {
        this.assistantId = assistantId;
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

    public String getObjectives() {
        return objectives;
    }

    public void setObjectives(String objectives) {
        this.objectives = objectives;
    }

    public String getRepositoryUrl() {
        return repositoryUrl;
    }

    public void setRepositoryUrl(String repositoryUrl) {
        this.repositoryUrl = repositoryUrl;
    }

    public DevelopmentProjectStatus getStatus() {
        return status;
    }

    public void setStatus(DevelopmentProjectStatus status) {
        this.status = status;
    }

    public String getProgressNote() {
        return progressNote;
    }

    public void setProgressNote(String progressNote) {
        this.progressNote = progressNote;
    }
}
