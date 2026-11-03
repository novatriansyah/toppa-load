package com.toppaload.identity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserDirectory {
    Optional<UserSummary> findActiveById(UUID id);
    List<UserSummary> listActiveUsers();
    AuthenticatedActor requireCurrentActor();
}
