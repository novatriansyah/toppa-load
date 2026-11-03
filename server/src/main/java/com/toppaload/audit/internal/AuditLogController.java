package com.toppaload.audit.internal;

import com.toppaload.audit.AuditEntry;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit-logs")
class AuditLogController {

    private final JdbcClient jdbc;

    AuditLogController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    List<AuditEntry> list(
            @RequestParam(required = false) String entityType,
            @RequestParam(required = false) String entityId,
            @RequestParam(defaultValue = "50") int limit
    ) {
        StringBuilder sql = new StringBuilder("""
            SELECT id, actor_id, actor_email, action, entity_type, entity_id,
                   CAST(before_data AS text) AS before_data,
                   CAST(after_data AS text) AS after_data,
                   occurred_at
            FROM audit_logs
            WHERE 1=1
            """);

        var params = new HashMap<String, Object>();

        if (entityType != null && !entityType.isBlank()) {
            sql.append(" AND entity_type = :entityType");
            params.put("entityType", entityType);
        }

        if (entityId != null && !entityId.isBlank()) {
            sql.append(" AND entity_id = :entityId");
            params.put("entityId", entityId);
        }

        sql.append(" ORDER BY occurred_at DESC LIMIT :limit");
        params.put("limit", limit > 0 ? limit : 50);

        var statement = jdbc.sql(sql.toString());
        params.forEach(statement::param);

        return statement.query((rs, rowNum) -> {
            Timestamp ts = rs.getTimestamp("occurred_at");
            return new AuditEntry(
                    rs.getObject("id", UUID.class),
                    rs.getObject("actor_id", UUID.class),
                    rs.getString("actor_email"),
                    rs.getString("action"),
                    rs.getString("entity_type"),
                    rs.getString("entity_id"),
                    rs.getString("before_data"),
                    rs.getString("after_data"),
                    ts != null ? ts.toInstant() : null
            );
        }).list();
    }
}
