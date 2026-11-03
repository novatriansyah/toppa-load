package com.toppaload.project.internal;

import com.toppaload.project.TaskSummary;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
class TaskController {

    private final ProjectService projectService;

    TaskController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping("/api/v1/projects/{projectId}/tasks")
    List<TaskSummary> listTasks(
            @PathVariable UUID projectId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status
    ) {
        return projectService.listTasks(projectId, q, status);
    }

    @PostMapping("/api/v1/projects/{projectId}/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    TaskSummary createTask(
            @PathVariable UUID projectId,
            @Valid @RequestBody ProjectService.CreateTaskRequest request
    ) {
        return projectService.createTask(projectId, request);
    }

    @GetMapping("/api/v1/tasks/{id}")
    TaskSummary getTask(@PathVariable UUID id) {
        return projectService.getTask(id);
    }

    @PutMapping("/api/v1/tasks/{id}")
    TaskSummary updateTask(
            @PathVariable UUID id,
            @Valid @RequestBody ProjectService.UpdateTaskRequest request
    ) {
        return projectService.updateTask(id, request);
    }

    @DeleteMapping("/api/v1/tasks/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR')")
    void deleteTask(@PathVariable UUID id) {
        projectService.deleteTask(id);
    }
}
