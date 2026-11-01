package com.toppaload;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class FlywayBaselineMigrationIT {

    @Autowired
    JdbcClient jdbc;

    @Test
    void seedsDefaultSystemSettingsRowOnFreshDatabase() {
        var row = jdbc.sql("SELECT underutilized_threshold_pct, overloaded_threshold_pct, work_timezone FROM system_settings WHERE id = 1")
                .query((rs, n) -> List.of(rs.getBigDecimal(1), rs.getBigDecimal(2), rs.getString(3)))
                .single();
        assertThat(row).containsExactly(new BigDecimal("60.0"), new BigDecimal("100.0"), "Asia/Jakarta");
    }

    @Test
    void enforcesCaseInsensitiveUniqueActiveEmailButAllowsReInsertAfterSoftDelete() {
        jdbc.sql("INSERT INTO users (email, full_name, role) VALUES ('Budi@Kantor.id', 'Budi', 'MEMBER')").update();
        assertThatThrownBy(() ->
                jdbc.sql("INSERT INTO users (email, full_name, role) VALUES ('budi@kantor.id', 'Budi Dup', 'MEMBER')").update()
        ).isInstanceOf(DataIntegrityViolationException.class);

        jdbc.sql("UPDATE users SET deleted_at = NOW() WHERE LOWER(email) = 'budi@kantor.id'").update();
        int inserted = jdbc.sql("INSERT INTO users (email, full_name, role) VALUES ('budi@kantor.id', 'Budi Baru', 'MEMBER')").update();
        assertThat(inserted).isEqualTo(1);
    }
}

