package com.toppaload.forecasting;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateScenarioRequest(
        @NotBlank @Size(max = 150) String name,
        String description
) {}

