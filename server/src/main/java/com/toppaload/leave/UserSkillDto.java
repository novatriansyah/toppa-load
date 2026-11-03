package com.toppaload.leave;

import java.util.UUID;

public record UserSkillDto(
        UUID id,
        UUID userId,
        String skillName,
        String proficiencyLevel
) {}

