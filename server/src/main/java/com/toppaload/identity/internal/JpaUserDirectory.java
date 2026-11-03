package com.toppaload.identity.internal;

import com.toppaload.identity.AuthenticatedActor;
import com.toppaload.identity.UserDirectory;
import com.toppaload.identity.UserSummary;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
class JpaUserDirectory implements UserDirectory {

    private final UserRepository userRepository;

    JpaUserDirectory(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Optional<UserSummary> findActiveById(UUID id) {
        return userRepository.findByIdAndDeletedAtIsNullAndActiveTrue(id)
                .map(this::toSummary);
    }

    @Override
    public List<UserSummary> listActiveUsers() {
        return userRepository.findByDeletedAtIsNullAndActiveTrueOrderByFullNameAsc().stream()
                .map(this::toSummary)
                .toList();
    }

    @Override
    public AuthenticatedActor requireCurrentActor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthenticated");
        }

        String identifier = extractIdentifier(auth);
        UserEntity user = findUser(userRepository, identifier);
        if (user == null || !user.isActive()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found or inactive");
        }

        return new AuthenticatedActor(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.getWorkHoursPerDay(),
                user.getWorkDays()
        );
    }

    private UserSummary toSummary(UserEntity user) {
        return new UserSummary(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole(),
                user.isActive(),
                user.getWorkHoursPerDay(),
                user.getWorkDays(),
                user.isEmailNotifications(),
                user.getPasswordHash() != null && !user.getPasswordHash().isBlank()
        );
    }

    static String extractIdentifier(Authentication auth) {
        if (auth == null) {
            return null;
        }
        if (auth.getPrincipal() instanceof OidcUser oidcUser) {
            String email = oidcUser.getEmail();
            if (email != null && !email.isBlank()) {
                return email;
            }
            Object emailAttr = oidcUser.getAttribute("email");
            if (emailAttr != null && !emailAttr.toString().isBlank()) {
                return emailAttr.toString();
            }
            return oidcUser.getName();
        }
        if (auth.getPrincipal() instanceof OAuth2User oauth2User) {
            Object emailAttr = oauth2User.getAttribute("email");
            if (emailAttr != null && !emailAttr.toString().isBlank()) {
                return emailAttr.toString();
            }
            return oauth2User.getName();
        }
        return auth.getName();
    }

    static UserEntity findUser(UserRepository userRepository, String identifier) {
        if (identifier == null || identifier.isBlank()) {
            return null;
        }
        try {
            UUID id = UUID.fromString(identifier);
            var byId = userRepository.findByIdAndDeletedAtIsNull(id);
            if (byId.isPresent()) {
                return byId.get();
            }
        } catch (IllegalArgumentException ignored) {
            // not a UUID
        }
        return userRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(identifier).orElse(null);
    }
}
