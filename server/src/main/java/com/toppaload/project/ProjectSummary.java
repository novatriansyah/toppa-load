package com.toppaload.project;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record ProjectSummary(
    UUID id,
    String code,
    String name,
    String description,
    String status,
    LocalDate startDate,
    LocalDate endDate,
    UUID ownerId,
    String ownerName,
    int taskCount,
    Instant createdAt,
    Instant updatedAt
) {}
