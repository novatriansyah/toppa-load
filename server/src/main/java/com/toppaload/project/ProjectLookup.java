package com.toppaload.project;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

public interface ProjectLookup {
    ProjectSummary requireActiveProject(UUID projectId);
    Optional<TaskSummary> findActiveTask(UUID taskId);
    ProjectSummary findOrCreateActiveByCode(String code, String name, LocalDate startDate, LocalDate endDate, UUID ownerId);
}
