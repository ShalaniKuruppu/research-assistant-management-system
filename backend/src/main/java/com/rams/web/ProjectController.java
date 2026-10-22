package com.rams.web;

import com.rams.domain.UserAccount;
import com.rams.service.CurrentUser;
import com.rams.service.ProjectService;
import com.rams.web.dto.ProjectDtos.AssignAssistantRequest;
import com.rams.web.dto.ProjectDtos.AssignmentResponse;
import com.rams.web.dto.ProjectDtos.AssignmentResult;
import com.rams.web.dto.ProjectDtos.CreateProjectRequest;
import com.rams.web.dto.ProjectDtos.ProjectResponse;
import com.rams.web.dto.ProjectDtos.UpdateAssignmentRequest;
import com.rams.web.dto.ProjectDtos.UpdateProjectRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final CurrentUser currentUser;
    private final ProjectService projects;

    public ProjectController(CurrentUser currentUser, ProjectService projects) {
        this.currentUser = currentUser;
        this.projects = projects;
    }

    @PostMapping
    public ResponseEntity<ProjectResponse> create(@Valid @RequestBody CreateProjectRequest request) {
        UserAccount user = currentUser.require();
        return ResponseEntity.status(HttpStatus.CREATED).body(projects.create(user, request));
    }

    @GetMapping
    public List<ProjectResponse> list() {
        return projects.list(currentUser.require());
    }

    @GetMapping("/{projectId}")
    public ProjectResponse get(@PathVariable Long projectId) {
        return projects.get(currentUser.require(), projectId);
    }

    @PatchMapping("/{projectId}")
    public ProjectResponse update(@PathVariable Long projectId, @Valid @RequestBody UpdateProjectRequest request) {
        return projects.update(currentUser.require(), projectId, request);
    }

    @PostMapping("/{projectId}/assistants")
    public ResponseEntity<AssignmentResponse> assign(
            @PathVariable Long projectId,
            @Valid @RequestBody AssignAssistantRequest request
    ) {
        AssignmentResult result = projects.assign(currentUser.require(), projectId, request.assistantId());
        HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(result.assignment());
    }

    @GetMapping("/{projectId}/assistants")
    public List<AssignmentResponse> assistants(@PathVariable Long projectId) {
        return projects.listAssistants(currentUser.require(), projectId);
    }

    @PatchMapping("/{projectId}/assistants/{assistantId}")
    public AssignmentResponse setAssignmentActive(
            @PathVariable Long projectId,
            @PathVariable Long assistantId,
            @Valid @RequestBody UpdateAssignmentRequest request
    ) {
        return projects.setAssignmentActive(currentUser.require(), projectId, assistantId, request.active());
    }
}
