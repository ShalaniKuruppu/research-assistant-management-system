package com.rams.web;

import com.rams.domain.UserAccount;
import com.rams.service.CurrentUser;
import com.rams.service.TaskService;
import com.rams.web.dto.TaskDtos.CreateTaskRequest;
import com.rams.web.dto.TaskDtos.CreateTaskUpdateRequest;
import com.rams.web.dto.TaskDtos.ProgressResponse;
import com.rams.web.dto.TaskDtos.TaskResponse;
import com.rams.web.dto.TaskDtos.TaskUpdateResponse;
import com.rams.web.dto.TaskDtos.UpdateTaskRequest;
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
@RequestMapping("/api")
public class TaskController {

    private final CurrentUser currentUser;
    private final TaskService tasks;

    public TaskController(CurrentUser currentUser, TaskService tasks) {
        this.currentUser = currentUser;
        this.tasks = tasks;
    }

    @PostMapping("/projects/{projectId}/tasks")
    public ResponseEntity<TaskResponse> create(
            @PathVariable Long projectId,
            @Valid @RequestBody CreateTaskRequest request
    ) {
        UserAccount user = currentUser.require();
        return ResponseEntity.status(HttpStatus.CREATED).body(tasks.create(user, projectId, request));
    }

    @GetMapping("/projects/{projectId}/tasks")
    public List<TaskResponse> list(@PathVariable Long projectId) {
        return tasks.list(currentUser.require(), projectId);
    }

    @GetMapping("/tasks/{taskId}")
    public TaskResponse get(@PathVariable Long taskId) {
        return tasks.get(currentUser.require(), taskId);
    }

    @PatchMapping("/tasks/{taskId}")
    public TaskResponse update(@PathVariable Long taskId, @Valid @RequestBody UpdateTaskRequest request) {
        return tasks.update(currentUser.require(), taskId, request);
    }

    @PostMapping("/tasks/{taskId}/updates")
    public ResponseEntity<ProgressResponse> addUpdate(
            @PathVariable Long taskId,
            @Valid @RequestBody CreateTaskUpdateRequest request
    ) {
        UserAccount user = currentUser.require();
        return ResponseEntity.status(HttpStatus.CREATED).body(tasks.addUpdate(user, taskId, request));
    }

    @GetMapping("/tasks/{taskId}/updates")
    public List<TaskUpdateResponse> listUpdates(@PathVariable Long taskId) {
        return tasks.listUpdates(currentUser.require(), taskId);
    }
}
