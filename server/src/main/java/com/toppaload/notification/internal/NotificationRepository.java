package com.toppaload.notification.internal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.toppaload.notification.NotificationType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
class NotificationRepository {

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    NotificationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    record NotificationRow(
            UUID id,
            UUID userId,
            NotificationType type,
            String title,
            Map<String, Object> payload,
            String dedupKey,
            Instant readAt,
            String emailStatus,
            Instant emailSentAt,
            String emailError,
            Instant createdAt
    ) {}

    public boolean insertIfAbsent(
            UUID userId,
            NotificationType type,
            String title,
            Map<String, Object> payload,
            String dedupKey,
            String emailStatus
    ) {
        String payloadJson = "{}";
        try {
            if (payload != null) {
                payloadJson = objectMapper.writeValueAsString(payload);
            }
        } catch (Exception ignored) {}

        String sql = """
            INSERT INTO notifications (id, user_id, type, title, payload, dedup_key, email_status, created_at)
            VALUES (gen_random_uuid(), :userId, :type, :title, CAST(:payload AS jsonb), :dedupKey, :emailStatus, NOW())
            ON CONFLICT (dedup_key) DO NOTHING
            RETURNING id
        """;

        Optional<UUID> insertedId = jdbc.sql(sql)
                .param("userId", userId)
                .param("type", type.name())
                .param("title", title)
                .param("payload", payloadJson)
                .param("dedupKey", dedupKey)
                .param("emailStatus", emailStatus)
                .query(UUID.class)
                .optional();

        return insertedId.isPresent();
    }

    public List<NotificationRow> findRecentByUserId(UUID userId, int limit) {
        String sql = """
            SELECT id, user_id, type, title, payload, dedup_key, read_at, email_status, email_sent_at, email_error, created_at
            FROM notifications
            WHERE user_id = :userId
            ORDER BY created_at DESC
            LIMIT :limit
        """;
        return jdbc.sql(sql)
                .param("userId", userId)
                .param("limit", limit)
                .query((rs, rowNum) -> toRow(rs))
                .list();
    }

    public int countUnreadByUserId(UUID userId) {
        String sql = "SELECT COUNT(*) FROM notifications WHERE user_id = :userId AND read_at IS NULL";
        Integer count = jdbc.sql(sql).param("userId", userId).query(Integer.class).single();
        return count != null ? count : 0;
    }

    public boolean markAsRead(UUID id, UUID userId) {
        String sql = "UPDATE notifications SET read_at = NOW() WHERE id = :id AND user_id = :userId AND read_at IS NULL";
        return jdbc.sql(sql).param("id", id).param("userId", userId).update() > 0;
    }

    public int markAllAsRead(UUID userId) {
        String sql = "UPDATE notifications SET read_at = NOW() WHERE user_id = :userId AND read_at IS NULL";
        return jdbc.sql(sql).param("userId", userId).update();
    }

    public List<NotificationRow> findPendingEmails(int limit) {
        String sql = """
            SELECT id, user_id, type, title, payload, dedup_key, read_at, email_status, email_sent_at, email_error, created_at
            FROM notifications
            WHERE email_status = 'PENDING'
            ORDER BY created_at ASC
            LIMIT :limit
        """;
        return jdbc.sql(sql)
                .param("limit", limit)
                .query((rs, rowNum) -> toRow(rs))
                .list();
    }

    public void updateEmailStatus(UUID id, String status, Instant sentAt, String error) {
        String safeError = error;
        if (safeError != null && safeError.length() > 500) {
            safeError = safeError.substring(0, 500);
        }
        String sql = """
            UPDATE notifications
            SET email_status = :status,
                email_sent_at = :sentAt,
                email_error = :error
            WHERE id = :id
        """;
        jdbc.sql(sql)
                .param("id", id)
                .param("status", status)
                .param("sentAt", sentAt != null ? java.sql.Timestamp.from(sentAt) : null)
                .param("error", safeError)
                .update();
    }

    private NotificationRow toRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> payloadMap = Collections.emptyMap();
        String json = rs.getString("payload");
        if (json != null && !json.isBlank()) {
            try {
                payloadMap = objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
            } catch (Exception ignored) {}
        }

        java.sql.Timestamp readTs = rs.getTimestamp("read_at");
        java.sql.Timestamp sentTs = rs.getTimestamp("email_sent_at");
        java.sql.Timestamp createdTs = rs.getTimestamp("created_at");

        return new NotificationRow(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                NotificationType.valueOf(rs.getString("type")),
                rs.getString("title"),
                payloadMap,
                rs.getString("dedup_key"),
                readTs != null ? readTs.toInstant() : null,
                rs.getString("email_status"),
                sentTs != null ? sentTs.toInstant() : null,
                rs.getString("email_error"),
                createdTs != null ? createdTs.toInstant() : null
        );
    }
}

