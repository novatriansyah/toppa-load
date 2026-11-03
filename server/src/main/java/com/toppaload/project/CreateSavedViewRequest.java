package com.toppaload.project;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

public record CreateSavedViewRequest(
        @NotBlank String viewType,
        @NotBlank String name,
        Map<String, Object> filterStateJson,
        boolean isShared
) {}

