package com.toppaload.audit;

import java.time.Instant;
import java.util.UUID;

public record AuditEntry(
    UUID id,
    UUID actorId,
    String actorEmail,
    String action,
    String entityType,
    String entityId,
    String beforeData,
    String afterData,
    Instant createdAt
) {}
