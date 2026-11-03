package com.toppaload.identity.internal;

import com.toppaload.identity.AuthenticatedActor;
import com.toppaload.identity.UserDirectory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
class AuthMeController {

    private final UserDirectory userDirectory;

    AuthMeController(UserDirectory userDirectory) {
        this.userDirectory = userDirectory;
    }

    @GetMapping
    AuthenticatedActor me() {
        return userDirectory.requireCurrentActor();
    }
}
