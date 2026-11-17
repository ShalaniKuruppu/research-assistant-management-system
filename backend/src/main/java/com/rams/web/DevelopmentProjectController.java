package com.rams.web;

import com.rams.service.CurrentUser;
import com.rams.service.DevelopmentProjectService;
import com.rams.web.dto.DevelopmentProjectDtos.CreateDevelopmentProjectRequest;
import com.rams.web.dto.DevelopmentProjectDtos.DevelopmentProjectResponse;
import com.rams.web.dto.DevelopmentProjectDtos.UpdateDevelopmentProjectRequest;
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
@RequestMapping("/api/development-projects")
public class DevelopmentProjectController {

    private final CurrentUser currentUser;
    private final DevelopmentProjectService developmentProjects;

    public DevelopmentProjectController(CurrentUser currentUser, DevelopmentProjectService developmentProjects) {
        this.currentUser = currentUser;
        this.developmentProjects = developmentProjects;
    }

    @GetMapping
    public List<DevelopmentProjectResponse> list() {
        return developmentProjects.list(currentUser.require());
    }

    @PostMapping
    public ResponseEntity<DevelopmentProjectResponse> create(@Valid @RequestBody CreateDevelopmentProjectRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(developmentProjects.create(currentUser.require(), request));
    }

    @GetMapping("/{id}")
    public DevelopmentProjectResponse get(@PathVariable Long id) {
        return developmentProjects.get(currentUser.require(), id);
    }

    @PatchMapping("/{id}")
    public DevelopmentProjectResponse update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateDevelopmentProjectRequest request
    ) {
        return developmentProjects.update(currentUser.require(), id, request);
    }
}
