package com.toppaload.audit;

import java.util.UUID;

public interface AuditRecorder {
    void record(UUID actorId, String actorEmail, String action, String entityType, String entityId, Object beforeData, Object afterData);
}
