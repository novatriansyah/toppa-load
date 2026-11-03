package com.toppaload.identity.internal;

import com.toppaload.audit.AuditRecorder;
import com.toppaload.identity.AuthenticatedActor;
import com.toppaload.identity.SystemSettingsDto;
import com.toppaload.identity.SystemSettingsReader;
import com.toppaload.identity.UserDirectory;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/settings")
class SystemSettingsController implements SystemSettingsReader {

    private final JdbcClient jdbc;
    private final UserDirectory userDirectory;
    private final AuditRecorder auditRecorder;

    SystemSettingsController(JdbcClient jdbc, UserDirectory userDirectory, AuditRecorder auditRecorder) {
        this.jdbc = jdbc;
        this.userDirectory = userDirectory;
        this.auditRecorder = auditRecorder;
    }

    @Override
    @GetMapping
    public SystemSettingsDto currentSettings() {
        return jdbc.sql("""
            SELECT underutilized_threshold_pct, overloaded_threshold_pct, work_timezone
            FROM system_settings
            WHERE id = 1
        """)
        .query((rs, rowNum) -> new SystemSettingsDto(
                rs.getBigDecimal("underutilized_threshold_pct"),
                rs.getBigDecimal("overloaded_threshold_pct"),
                rs.getString("work_timezone")
        ))
        .single();
    }

    @PutMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public SystemSettingsDto updateSettings(@Valid @RequestBody SystemSettingsDto dto) {
        AuthenticatedActor actor = userDirectory.requireCurrentActor();

        if (dto.underutilizedThresholdPct().compareTo(dto.overloadedThresholdPct()) >= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ambang batas kurang beban harus lebih kecil dari ambang batas kelebihan beban.");
        }

        try {
            ZoneId.of(dto.workTimezone());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Zona waktu tidak valid: " + dto.workTimezone());
        }

        SystemSettingsDto before = currentSettings();

        jdbc.sql("""
            UPDATE system_settings
            SET underutilized_threshold_pct = :under,
                overloaded_threshold_pct = :over,
                work_timezone = :tz,
                updated_by = :userId,
                updated_at = NOW()
            WHERE id = 1
        """)
        .param("under", dto.underutilizedThresholdPct())
        .param("over", dto.overloadedThresholdPct())
        .param("tz", dto.workTimezone())
        .param("userId", actor.userId())
        .update();

        auditRecorder.record(
                actor.userId(),
                actor.email(),
                "UPDATE",
                "SYSTEM_SETTINGS",
                "1",
                Map.of(
                        "underutilizedThresholdPct", before.underutilizedThresholdPct(),
                        "overloadedThresholdPct", before.overloadedThresholdPct(),
                        "workTimezone", before.workTimezone()
                ),
                Map.of(
                        "underutilizedThresholdPct", dto.underutilizedThresholdPct(),
                        "overloadedThresholdPct", dto.overloadedThresholdPct(),
                        "workTimezone", dto.workTimezone()
                )
        );

        return currentSettings();
    }
}

