package com.toppaload.leave;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record UpdateUserSkillsRequest(
        List<SkillInput> skills
) {
    public record SkillInput(
            @NotBlank String skillName,
            String proficiencyLevel
    ) {}
}

