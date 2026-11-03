package com.toppaload.project;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record TaskSummary(
    UUID id,
    UUID projectId,
    String projectCode,
    String projectName,
    String title,
    String description,
    String status,
    String priority,
    BigDecimal estimatedHours,
    LocalDate startDate,
    LocalDate dueDate,
    int progressPercent,
    UUID assigneeId,
    String assigneeName,
    List<String> labels,
    Instant createdAt,
    Instant updatedAt
) {}
