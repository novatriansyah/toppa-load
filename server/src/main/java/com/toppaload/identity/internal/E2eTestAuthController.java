package com.toppaload.identity.internal;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/e2e")
@Profile("e2e")
public class E2eTestAuthController {

    private final JdbcClient jdbc;

    public E2eTestAuthController(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record E2eUserRow(UUID id, String email, String fullName, String role, boolean active) {}

    @PostMapping("/login")
    public ResponseEntity<E2eUserRow> loginAs(
            @RequestParam("email") String email,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        E2eUserRow user = jdbc.sql("""
            SELECT id, email, full_name, role, active
            FROM users
            WHERE lower(email) = lower(:email) AND deleted_at IS NULL
        """)
        .param("email", email)
        .query((rs, rowNum) -> new E2eUserRow(
                UUID.fromString(rs.getString("id")),
                rs.getString("email"),
                rs.getString("full_name"),
                rs.getString("role"),
                rs.getBoolean("active")
        ))
        .optional()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User allowlist not found: " + email));

        if (!user.active()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User is inactive: " + email);
        }

        Instant now = Instant.now();
        OidcIdToken idToken = new OidcIdToken(
                "mock-token-value",
                now,
                now.plusSeconds(3600),
                Map.of(
                        "sub", user.id().toString(),
                        "email", user.email(),
                        "name", user.fullName(),
                        "preferred_username", user.email()
                )
        );

        List<SimpleGrantedAuthority> authorities = List.of(new SimpleGrantedAuthority("ROLE_" + user.role()));
        DefaultOidcUser oidcUser = new DefaultOidcUser(authorities, idToken, "email");

        OAuth2AuthenticationToken authToken = new OAuth2AuthenticationToken(
                oidcUser,
                authorities,
                "google"
        );

        SecurityContext securityContext = SecurityContextHolder.createEmptyContext();
        securityContext.setAuthentication(authToken);
        SecurityContextHolder.setContext(securityContext);

        HttpSession session = request.getSession(true);
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, securityContext);

        return ResponseEntity.ok(user);
    }
}

