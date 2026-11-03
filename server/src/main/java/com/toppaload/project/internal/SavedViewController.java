package com.toppaload.project.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toppaload.identity.AuthenticatedActor;
import com.toppaload.identity.Role;
import com.toppaload.identity.UserDirectory;
import com.toppaload.project.CreateSavedViewRequest;
import com.toppaload.project.SavedViewDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/v1/saved-views")
class SavedViewController {

    private final JdbcClient jdbc;
    private final UserDirectory userDirectory;
    private final ObjectMapper objectMapper;

    SavedViewController(JdbcClient jdbc, UserDirectory userDirectory) {
        this.jdbc = jdbc;
        this.userDirectory = userDirectory;
        this.objectMapper = new ObjectMapper();
    }

    private SavedViewDto mapRow(ResultSet rs, int rowNum) throws SQLException {
        String filterJson = rs.getString("filter_state_json");
        Map<String, Object> filterMap = Collections.emptyMap();
        if (filterJson != null && !filterJson.isBlank()) {
            try {
                filterMap = objectMapper.readValue(filterJson, new TypeReference<>() {});
            } catch (JsonProcessingException ignored) {
            }
        }

        OffsetDateTime createdAt = rs.getObject("created_at", OffsetDateTime.class);
        return new SavedViewDto(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("user_id")),
                rs.getString("view_type"),
                rs.getString("name"),
                filterMap,
                rs.getBoolean("is_shared"),
                createdAt != null ? createdAt.toInstant() : null
        );
    }

    @GetMapping
    public List<SavedViewDto> listSavedViews(@RequestParam(name = "viewType", required = false) String viewType) {
        AuthenticatedActor actor = userDirectory.requireCurrentActor();

        if (viewType != null && !viewType.isBlank()) {
            return jdbc.sql("""
                SELECT id, user_id, view_type, name, filter_state_json, is_shared, created_at
                FROM saved_views
                WHERE (user_id = :userId OR is_shared = true) AND view_type = :viewType
                ORDER BY created_at DESC
            """)
            .param("userId", actor.userId())
            .param("viewType", viewType)
            .query(this::mapRow)
            .list();
        }

        return jdbc.sql("""
            SELECT id, user_id, view_type, name, filter_state_json, is_shared, created_at
            FROM saved_views
            WHERE (user_id = :userId OR is_shared = true)
            ORDER BY created_at DESC
        """)
        .param("userId", actor.userId())
        .query(this::mapRow)
        .list();
    }

    @PostMapping
    public ResponseEntity<SavedViewDto> createSavedView(@Valid @RequestBody CreateSavedViewRequest request) {
        AuthenticatedActor actor = userDirectory.requireCurrentActor();
        UUID id = UUID.randomUUID();

        String jsonStr = "{}";
        if (request.filterStateJson() != null) {
            try {
                jsonStr = objectMapper.writeValueAsString(request.filterStateJson());
            } catch (JsonProcessingException e) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid filter JSON structure.");
            }
        }

        jdbc.sql("""
            INSERT INTO saved_views (id, user_id, view_type, name, filter_state_json, is_shared, created_at, updated_at)
            VALUES (:id, :userId, :viewType, :name, :filterJson::jsonb, :isShared, NOW(), NOW())
        """)
        .param("id", id)
        .param("userId", actor.userId())
        .param("viewType", request.viewType())
        .param("name", request.name())
        .param("filterJson", jsonStr)
        .param("isShared", request.isShared())
        .update();

        SavedViewDto created = jdbc.sql("""
            SELECT id, user_id, view_type, name, filter_state_json, is_shared, created_at
            FROM saved_views
            WHERE id = :id
        """)
        .param("id", id)
        .query(this::mapRow)
        .single();

        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSavedView(@PathVariable("id") UUID id) {
        AuthenticatedActor actor = userDirectory.requireCurrentActor();

        Optional<SavedViewDto> existing = jdbc.sql("SELECT id, user_id, view_type, name, filter_state_json, is_shared, created_at FROM saved_views WHERE id = :id")
                .param("id", id)
                .query(this::mapRow)
                .optional();

        if (existing.isEmpty()) {
            return ResponseEntity.noContent().build();
        }

        SavedViewDto view = existing.get();
        if (!view.userId().equals(actor.userId()) && actor.role() != Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Anda tidak memiliki akses untuk menghapus tampilan tersimpan ini.");
        }

        jdbc.sql("DELETE FROM saved_views WHERE id = :id")
                .param("id", id)
                .update();

        return ResponseEntity.noContent().build();
    }
}

