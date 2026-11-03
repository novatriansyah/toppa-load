package com.toppaload.identity.internal;

import com.toppaload.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AllowlistOidcUserServiceTest {

    @Autowired
    AllowlistOidcUserService allowlistOidcUserService;

    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void setup() {
        jdbc.sql("DELETE FROM audit_logs").update();
        jdbc.sql("DELETE FROM users WHERE email != 'admin@toppaload.local'").update();
        Integer count = jdbc.sql("SELECT COUNT(*) FROM users WHERE email = 'admin@toppaload.local'").query(Integer.class).single();
        if (count == null || count == 0) {
            jdbc.sql("""
                INSERT INTO users (email, full_name, role, active, work_hours_per_day, work_days)
                VALUES ('admin@toppaload.local', 'Bootstrap Admin', 'ADMIN', true, 8.00, '{1,2,3,4,5}')
            """).update();
        }
    }

    @Test
    void rejectsUnlistedUserAndRecordsAudit() {
        OidcIdToken idToken = new OidcIdToken(
                "mock-token",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("sub", "unlisted-sub", "email", "stranger@external.com")
        );
        OidcUser unlistedOidcUser = new DefaultOidcUser(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                idToken
        );

        assertThatThrownBy(() -> allowlistOidcUserService.processOidcUser(null, unlistedOidcUser))
                .isInstanceOf(OAuth2AuthenticationException.class);

        Integer loginDeniedCount = jdbc.sql("""
            SELECT COUNT(*) FROM audit_logs
            WHERE action = 'LOGIN_DENIED' AND entity_id = 'stranger@external.com'
        """).query(Integer.class).single();
        assertThat(loginDeniedCount).isEqualTo(1);
    }

    @Test
    void rejectsInactiveUserAndRecordsAudit() {
        jdbc.sql("""
            INSERT INTO users (email, full_name, role, active)
            VALUES ('inactive@kantor.id', 'Inactive User', 'MEMBER', false)
        """).update();

        OidcIdToken idToken = new OidcIdToken(
                "mock-token-inactive",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("sub", "inactive-sub", "email", "inactive@kantor.id")
        );
        OidcUser inactiveOidcUser = new DefaultOidcUser(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                idToken
        );

        assertThatThrownBy(() -> allowlistOidcUserService.processOidcUser(null, inactiveOidcUser))
                .isInstanceOf(OAuth2AuthenticationException.class);

        Integer loginDeniedCount = jdbc.sql("""
            SELECT COUNT(*) FROM audit_logs
            WHERE action = 'LOGIN_DENIED' AND entity_id = 'inactive@kantor.id'
        """).query(Integer.class).single();
        assertThat(loginDeniedCount).isEqualTo(1);
    }

    @Test
    void allowsActiveUserAndAssignsRoleAuthority() {
        OidcIdToken idToken = new OidcIdToken(
                "mock-token-admin",
                Instant.now(),
                Instant.now().plusSeconds(3600),
                Map.of("sub", "admin-sub", "email", "admin@toppaload.local")
        );
        OidcUser adminOidcUser = new DefaultOidcUser(
                List.of(new SimpleGrantedAuthority("ROLE_USER")),
                idToken
        );

        OidcUser processed = allowlistOidcUserService.processOidcUser(null, adminOidcUser);
        assertThat(processed.getAuthorities()).extracting("authority")
                .contains("ROLE_ADMIN");
    }
}
