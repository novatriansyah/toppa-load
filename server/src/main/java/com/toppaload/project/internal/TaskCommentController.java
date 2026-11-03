package com.toppaload.project.internal;

import com.toppaload.audit.AuditRecorder;
import com.toppaload.identity.AuthenticatedActor;
import com.toppaload.identity.Role;
import com.toppaload.identity.UserDirectory;
import com.toppaload.project.CreateTaskCommentRequest;
import com.toppaload.project.TaskCommentDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
class TaskCommentController {

    private final JdbcClient jdbc;
    private final UserDirectory userDirectory;
    private final TaskRepository taskRepository;
    private final AuditRecorder auditRecorder;

    TaskCommentController(
            JdbcClient jdbc,
            UserDirectory userDirectory,
            TaskRepository taskRepository,
            AuditRecorder auditRecorder
    ) {
        this.jdbc = jdbc;
        this.userDirectory = userDirectory;
        this.taskRepository = taskRepository;
        this.auditRecorder = auditRecorder;
    }

    @GetMapping("/api/v1/tasks/{taskId}/comments")
    @Transactional(readOnly = true)
    List<TaskCommentDto> listComments(@PathVariable UUID taskId) {
        taskRepository.findByIdAndDeletedAtIsNull(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task tidak ditemukan: " + taskId));

        return jdbc.sql("""
            SELECT c.id, c.task_id, c.author_id, u.full_name as author_full_name, u.email as author_email,
                   c.comment_text, c.created_at, c.updated_at
            FROM task_comments c
            JOIN users u ON u.id = c.author_id
            WHERE c.task_id = :taskId AND c.deleted_at IS NULL
            ORDER BY c.created_at ASC
        """)
        .param("taskId", taskId)
        .query((rs, rowNum) -> {
            Timestamp createdTs = rs.getTimestamp("created_at");
            Timestamp updatedTs = rs.getTimestamp("updated_at");
            return new TaskCommentDto(
                    UUID.fromString(rs.getString("id")),
                    UUID.fromString(rs.getString("task_id")),
                    UUID.fromString(rs.getString("author_id")),
                    rs.getString("author_full_name"),
                    rs.getString("author_email"),
                    rs.getString("comment_text"),
                    createdTs != null ? createdTs.toInstant() : Instant.now(),
                    updatedTs != null ? updatedTs.toInstant() : Instant.now()
            );
        })
        .list();
    }

    @PostMapping("/api/v1/tasks/{taskId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    TaskCommentDto addComment(
            @PathVariable UUID taskId,
            @Valid @RequestBody CreateTaskCommentRequest request
    ) {
        TaskEntity task = taskRepository.findByIdAndDeletedAtIsNull(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task tidak ditemukan: " + taskId));

        AuthenticatedActor actor = userDirectory.requireCurrentActor();
        UUID commentId = UUID.randomUUID();
        Instant now = Instant.now();

        jdbc.sql("""
            INSERT INTO task_comments (id, task_id, author_id, comment_text, created_at, updated_at)
            VALUES (:id, :taskId, :authorId, :text, :now, :now)
        """)
        .param("id", commentId)
        .param("taskId", taskId)
        .param("authorId", actor.userId())
        .param("text", request.commentText().trim())
        .param("now", Timestamp.from(now))
        .update();

        auditRecorder.record(
                actor.userId(),
                actor.email(),
                "CREATE",
                "TASK_COMMENT",
                commentId.toString(),
                null,
                Map.of(
                        "taskId", taskId.toString(),
                        "taskTitle", task.getTitle(),
                        "commentText", request.commentText().trim()
                )
        );

        return new TaskCommentDto(
                commentId,
                taskId,
                actor.userId(),
                actor.fullName(),
                actor.email(),
                request.commentText().trim(),
                now,
                now
        );
    }

    @DeleteMapping("/api/v1/tasks/{taskId}/comments/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    void deleteComment(@PathVariable UUID taskId, @PathVariable UUID commentId) {
        AuthenticatedActor actor = userDirectory.requireCurrentActor();

        var comment = jdbc.sql("""
            SELECT id, author_id FROM task_comments
            WHERE id = :commentId AND task_id = :taskId AND deleted_at IS NULL
        """)
        .param("commentId", commentId)
        .param("taskId", taskId)
        .query((rs, rowNum) -> Map.of(
                "id", rs.getString("id"),
                "authorId", rs.getString("author_id")
        ))
        .optional()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Komentar tidak ditemukan"));

        UUID authorId = UUID.fromString((String) comment.get("authorId"));
        if (actor.role() == Role.MEMBER && !actor.userId().equals(authorId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Hanya dapat menghapus komentar milik sendiri");
        }

        jdbc.sql("UPDATE task_comments SET deleted_at = NOW() WHERE id = :commentId")
                .param("commentId", commentId)
                .update();

        auditRecorder.record(
                actor.userId(),
                actor.email(),
                "DELETE",
                "TASK_COMMENT",
                commentId.toString(),
                comment,
                null
        );
    }
}
