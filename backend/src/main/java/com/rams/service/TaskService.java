package com.rams.service;

import com.rams.domain.Project;
import com.rams.domain.Task;
import com.rams.domain.TaskPriority;
import com.rams.domain.TaskStatus;
import com.rams.domain.TaskUpdate;
import com.rams.domain.UserAccount;
import com.rams.repository.ProjectAssistantRepository;
import com.rams.repository.TaskRepository;
import com.rams.repository.TaskUpdateRepository;
import com.rams.web.ApiException;
import com.rams.web.Text;
import com.rams.web.dto.TaskDtos.CreateTaskRequest;
import com.rams.web.dto.TaskDtos.CreateTaskUpdateRequest;
import com.rams.web.dto.TaskDtos.ProgressResponse;
import com.rams.web.dto.TaskDtos.TaskResponse;
import com.rams.web.dto.TaskDtos.TaskUpdateResponse;
import com.rams.web.dto.TaskDtos.UpdateTaskRequest;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class TaskService {

    private final TaskRepository tasks;
    private final TaskUpdateRepository updates;
    private final ProjectAssistantRepository assignments;
    private final ProjectService projects;
    private final EntityManager entityManager;

    public TaskService(
            TaskRepository tasks,
            TaskUpdateRepository updates,
            ProjectAssistantRepository assignments,
            ProjectService projects,
            EntityManager entityManager
    ) {
        this.tasks = tasks;
        this.updates = updates;
        this.assignments = assignments;
        this.projects = projects;
        this.entityManager = entityManager;
    }

    @Transactional
    public TaskResponse create(UserAccount user, Long projectId, CreateTaskRequest request) {
        Project project = projects.requireOwner(user, projectId);
        requireActiveMember(projectId, request.assignedTo());
        String title = Text.required(request.title(), 200, "Title");
        ProjectService.requireDateOrder(request.startDate(), request.dueDate());
        Task task = new Task();
        task.setProjectId(project.getId());
        task.setAssignedTo(request.assignedTo());
        task.setCreatedBy(user.getId());
        task.setTitle(title);
        task.setDescription(Text.optional(request.description(), 20000, "Description"));
        task.setPriority(request.priority() == null ? TaskPriority.MEDIUM : request.priority());
        task.setStartDate(request.startDate());
        task.setDueDate(request.dueDate());
        task.setExpectedDeliverables(Text.optional(request.expectedDeliverables(), 20000, "Expected deliverables"));
        task.setActive(true);
        tasks.save(task);
        return toResponse(task);
    }

    public List<TaskResponse> list(UserAccount user, Long projectId) {
        Project project = projects.requireVisible(user, projectId);
        List<Task> found = project.getSupervisorId().equals(user.getId())
                ? tasks.findByProjectIdOrderByIdAsc(projectId)
                : tasks.findByProjectIdAndAssignedToOrderByIdAsc(projectId, user.getId());
        return found.stream().map(TaskService::toResponse).toList();
    }

    public TaskResponse get(UserAccount user, Long taskId) {
        return toResponse(requireVisibleTask(user, taskId));
    }

    @Transactional
    public TaskResponse update(UserAccount user, Long taskId, UpdateTaskRequest request) {
        Task task = tasks.findById(taskId).orElseThrow(() -> ApiException.notFound("Task not found"));
        projects.requireOwner(user, task.getProjectId());
        if (request.assignedTo() != null) {
            requireActiveMember(task.getProjectId(), request.assignedTo());
            task.setAssignedTo(request.assignedTo());
        }
        if (request.title() != null) {
            task.setTitle(Text.required(request.title(), 200, "Title"));
        }
        if (request.description() != null) {
            task.setDescription(Text.optional(request.description(), 20000, "Description"));
        }
        if (request.priority() != null) {
            task.setPriority(request.priority());
        }
        LocalDate start = request.startDate() == null ? task.getStartDate() : request.startDate();
        LocalDate due = request.dueDate() == null ? task.getDueDate() : request.dueDate();
        ProjectService.requireDateOrder(start, due);
        task.setStartDate(start);
        task.setDueDate(due);
        if (request.expectedDeliverables() != null) {
            task.setExpectedDeliverables(Text.optional(request.expectedDeliverables(), 20000, "Expected deliverables"));
        }
        if (request.active() != null) {
            task.setActive(request.active());
        }
        entityManager.flush();
        return toResponse(task);
    }

    @Transactional
    public ProgressResponse addUpdate(UserAccount user, Long taskId, CreateTaskUpdateRequest request) {
        Task task = requireProgressAuthor(user, taskId);
        if (request.status() == TaskStatus.BLOCKED && Text.trimToNull(request.blockerNote()) == null) {
            throw ApiException.badRequest("A blocked update needs a blocker note");
        }
        TaskUpdate update = new TaskUpdate();
        update.setTaskId(task.getId());
        update.setUpdatedBy(user.getId());
        update.setStatus(request.status());
        update.setCompletionPercentage(request.completionPercentage().shortValue());
        update.setProgressNote(Text.required(request.progressNote(), 20000, "Progress note"));
        update.setBlockerNote(Text.optional(request.blockerNote(), 20000, "Blocker note"));
        updates.save(update);
        entityManager.flush();
        entityManager.refresh(task);
        return new ProgressResponse(toUpdateResponse(update), toResponse(task));
    }

    public List<TaskUpdateResponse> listUpdates(UserAccount user, Long taskId) {
        Task task = requireVisibleTask(user, taskId);
        return updates.findByTaskIdOrderByCreatedAtAscIdAsc(task.getId()).stream()
                .map(TaskService::toUpdateResponse)
                .toList();
    }

    private Task requireVisibleTask(UserAccount user, Long taskId) {
        Task task = tasks.findById(taskId).orElseThrow(() -> ApiException.notFound("Task not found"));
        Project project = projects.requireVisible(user, task.getProjectId());
        if (project.getSupervisorId().equals(user.getId())) {
            return task;
        }
        if (task.getAssignedTo().equals(user.getId())) {
            return task;
        }
        throw ApiException.notFound("Task not found");
    }

    private Task requireProgressAuthor(UserAccount user, Long taskId) {
        Task task = tasks.findById(taskId).orElseThrow(() -> ApiException.notFound("Task not found"));
        Project project = projects.requireVisible(user, task.getProjectId());
        if (project.getSupervisorId().equals(user.getId()) || task.getAssignedTo().equals(user.getId())) {
            return task;
        }
        throw ApiException.notFound("Task not found");
    }

    private void requireActiveMember(Long projectId, Long assistantId) {
        if (!assignments.existsByProjectIdAndAssistantIdAndActiveTrue(projectId, assistantId)) {
            throw ApiException.badRequest("Assign the task to an active research assistant on this project");
        }
    }

    private static TaskResponse toResponse(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getProjectId(),
                task.getAssignedTo(),
                task.getCreatedBy(),
                task.getTitle(),
                task.getDescription(),
                task.getPriority(),
                task.getStartDate(),
                task.getDueDate(),
                task.getExpectedDeliverables(),
                task.getStatus(),
                task.getCompletionPercentage(),
                task.isActive(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }

    private static TaskUpdateResponse toUpdateResponse(TaskUpdate update) {
        return new TaskUpdateResponse(
                update.getId(),
                update.getTaskId(),
                update.getUpdatedBy(),
                update.getStatus(),
                update.getCompletionPercentage(),
                update.getProgressNote(),
                update.getBlockerNote(),
                update.getCreatedAt()
        );
    }
}
