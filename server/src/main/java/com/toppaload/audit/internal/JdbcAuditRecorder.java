package com.toppaload.audit.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toppaload.audit.AuditRecorder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
class JdbcAuditRecorder implements AuditRecorder {

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    @Autowired
    JdbcAuditRecorder(JdbcClient jdbc, ObjectProvider<ObjectMapper> objectMapperProvider) {
        this(jdbc, objectMapperProvider.getIfAvailable(() -> new ObjectMapper().findAndRegisterModules()));
    }

    JdbcAuditRecorder(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper().findAndRegisterModules();
    }

    @Override
    public void record(UUID actorId, String actorEmail, String action, String entityType, String entityId, Object beforeData, Object afterData) {
        String beforeJson = toJson(beforeData);
        String afterJson = toJson(afterData);

        jdbc.sql("""
            INSERT INTO audit_logs (id, actor_id, actor_email, action, entity_type, entity_id, before_data, after_data, occurred_at)
            VALUES (gen_random_uuid(), :actorId, :actorEmail, :action, :entityType, :entityId, CAST(:beforeData AS jsonb), CAST(:afterData AS jsonb), NOW())
            """)
            .param("actorId", actorId)
            .param("actorEmail", actorEmail)
            .param("action", action)
            .param("entityType", entityType)
            .param("entityId", entityId)
            .param("beforeData", beforeJson)
            .param("afterData", afterJson)
            .update();
    }

    private String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof String str) {
            String trimmed = str.trim();
            if ((trimmed.startsWith("{") && trimmed.endsWith("}")) || (trimmed.startsWith("[") && trimmed.endsWith("]"))) {
                return trimmed;
            }
        }
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize audit data to JSON", e);
        }
    }
}
