package com.toppaload.leave.internal;

import com.toppaload.identity.AuthenticatedActor;
import com.toppaload.identity.Role;
import com.toppaload.identity.UserDirectory;
import com.toppaload.leave.UpdateUserSkillsRequest;
import com.toppaload.leave.UserSkillDto;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
class SkillController {

    private final UserSkillService skillService;
    private final UserDirectory userDirectory;

    SkillController(UserSkillService skillService, UserDirectory userDirectory) {
        this.skillService = skillService;
        this.userDirectory = userDirectory;
    }

    @GetMapping("/api/v1/users/{userId}/skills")
    public List<UserSkillDto> getUserSkills(@PathVariable("userId") UUID userId) {
        return skillService.getUserSkills(userId);
    }

    @PutMapping("/api/v1/users/{userId}/skills")
    public List<UserSkillDto> updateUserSkills(
            @PathVariable("userId") UUID userId,
            @Valid @RequestBody UpdateUserSkillsRequest request
    ) {
        AuthenticatedActor actor = userDirectory.requireCurrentActor();
        if (actor.role() == Role.MEMBER && !actor.userId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Hanya dapat mengubah keahlian diri sendiri.");
        }
        return skillService.updateUserSkills(userId, request);
    }

    @GetMapping("/api/v1/skills/directory")
    public List<UserSkillDto> getSkillsDirectory() {
        return skillService.getSkillsDirectory();
    }
}

