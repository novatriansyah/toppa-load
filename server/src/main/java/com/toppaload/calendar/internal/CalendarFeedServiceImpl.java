package com.toppaload.calendar.internal;

import com.toppaload.calendar.CalendarFeedService;
import com.toppaload.calendar.CalendarFeedTokenDto;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
class CalendarFeedServiceImpl implements CalendarFeedService {

    private final JdbcClient jdbc;
    private final SecureRandom secureRandom = new SecureRandom();
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter UTC_STAMP_FMT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    CalendarFeedServiceImpl(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public CalendarFeedTokenDto getOrCreateToken(UUID userId, String baseUrl) {
        return jdbc.sql("""
            SELECT id, user_id, token, scope, created_at, last_accessed_at
            FROM calendar_feed_tokens
            WHERE user_id = :userId AND is_active = TRUE
            ORDER BY created_at DESC
            LIMIT 1
        """)
        .param("userId", userId)
        .query((rs, rowNum) -> {
            String token = rs.getString("token");
            return new CalendarFeedTokenDto(
                    (UUID) rs.getObject("id"),
                    (UUID) rs.getObject("user_id"),
                    formatFeedUrl(baseUrl, token),
                    token,
                    rs.getString("scope"),
                    rs.getTimestamp("created_at").toInstant(),
                    rs.getTimestamp("last_accessed_at") != null ? rs.getTimestamp("last_accessed_at").toInstant() : null
            );
        })
        .optional()
        .orElseGet(() -> rotateToken(userId, baseUrl));
    }

    @Override
    @Transactional
    public CalendarFeedTokenDto rotateToken(UUID userId, String baseUrl) {
        // Deactivate old tokens
        jdbc.sql("UPDATE calendar_feed_tokens SET is_active = FALSE WHERE user_id = :userId")
                .param("userId", userId)
                .update();

        // Generate 32 bytes secure random hex token
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        String token = HexFormat.of().formatHex(bytes);

        UUID id = UUID.randomUUID();
        jdbc.sql("""
            INSERT INTO calendar_feed_tokens (id, user_id, token, scope, is_active)
            VALUES (:id, :userId, :token, 'ALL', TRUE)
        """)
        .param("id", id)
        .param("userId", userId)
        .param("token", token)
        .update();

        return new CalendarFeedTokenDto(
                id,
                userId,
                formatFeedUrl(baseUrl, token),
                token,
                "ALL",
                Instant.now(),
                null
        );
    }

    @Override
    @Transactional
    public String generateICalendarFeed(String token) {
        var tokenRow = jdbc.sql("""
            SELECT id, user_id FROM calendar_feed_tokens
            WHERE token = :token AND is_active = TRUE
        """)
        .param("token", token)
        .query((rs, rowNum) -> new TokenRow((UUID) rs.getObject("id"), (UUID) rs.getObject("user_id")))
        .optional()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Feed kalender tidak ditemukan atau tautan telah dirotasi."));

        UUID userId = tokenRow.userId();

        // Update last accessed
        jdbc.sql("UPDATE calendar_feed_tokens SET last_accessed_at = NOW() WHERE id = :id")
                .param("id", tokenRow.id())
                .update();

        // Fetch user meta
        String userName = jdbc.sql("SELECT full_name FROM users WHERE id = :userId")
                .param("userId", userId)
                .query(String.class)
                .optional()
                .orElse("Karyawan Toppa Load");

        // Fetch allocations
        List<AllocEvent> allocations = jdbc.sql("""
            SELECT a.id, p.name AS project_name, a.start_date, a.end_date, a.hours_per_day, a.notes
            FROM allocations a
            JOIN projects p ON p.id = a.project_id
            WHERE a.user_id = :userId AND a.deleted_at IS NULL
            ORDER BY a.start_date ASC
        """)
        .param("userId", userId)
        .query((rs, rowNum) -> new AllocEvent(
                (UUID) rs.getObject("id"),
                rs.getString("project_name"),
                rs.getDate("start_date").toLocalDate(),
                rs.getDate("end_date").toLocalDate(),
                rs.getBigDecimal("hours_per_day"),
                rs.getString("notes")
        ))
        .list();

        // Fetch leaves
        List<LeaveEvent> leaves = jdbc.sql("""
            SELECT l.id, l.leave_type, l.portion, l.reason, l.start_date, l.end_date
            FROM leaves l
            WHERE l.user_id = :userId AND l.deleted_at IS NULL AND l.status = 'APPROVED'
            ORDER BY l.start_date ASC
        """)
        .param("userId", userId)
        .query((rs, rowNum) -> new LeaveEvent(
                (UUID) rs.getObject("id"),
                rs.getString("leave_type"),
                rs.getString("portion"),
                rs.getString("reason"),
                rs.getDate("start_date").toLocalDate(),
                rs.getDate("end_date").toLocalDate()
        ))
        .list();

        String nowUtc = UTC_STAMP_FMT.format(Instant.now());

        StringBuilder sb = new StringBuilder();
        sb.append("BEGIN:VCALENDAR\r\n");
        sb.append("VERSION:2.0\r\n");
        sb.append("PRODID:-//Toppa Load//Team Capacity Calendar//ID\r\n");
        sb.append("CALSCALE:GREGORIAN\r\n");
        sb.append("METHOD:PUBLISH\r\n");
        sb.append("X-WR-CALNAME:Toppa Load - ").append(escapeText(userName)).append("\r\n");
        sb.append("X-WR-TIMEZONE:Asia/Jakarta\r\n");

        // Allocations events
        for (AllocEvent a : allocations) {
            sb.append("BEGIN:VEVENT\r\n");
            sb.append("UID:alloc-").append(a.id()).append("@toppaload.local\r\n");
            sb.append("DTSTAMP:").append(nowUtc).append("\r\n");
            sb.append("DTSTART;VALUE=DATE:").append(DATE_FMT.format(a.startDate())).append("\r\n");
            // RFC 5545 whole day events have exclusive DTEND
            sb.append("DTEND;VALUE=DATE:").append(DATE_FMT.format(a.endDate().plusDays(1))).append("\r\n");
            sb.append("SUMMARY:").append(escapeText(a.projectName())).append(" (").append(a.hoursPerDay()).append(" jam/hari)\r\n");
            sb.append("DESCRIPTION:Alokasi Proyek Toppa Load: ").append(escapeText(a.projectName()))
              .append("\\nJam per hari: ").append(a.hoursPerDay())
              .append(a.notes() != null ? "\\nCatatan: " + escapeText(a.notes()) : "")
              .append("\r\n");
            sb.append("STATUS:CONFIRMED\r\n");
            sb.append("TRANSP:OPAQUE\r\n");
            sb.append("END:VEVENT\r\n");
        }

        // Leaves events
        for (LeaveEvent l : leaves) {
            sb.append("BEGIN:VEVENT\r\n");
            sb.append("UID:leave-").append(l.id()).append("@toppaload.local\r\n");
            sb.append("DTSTAMP:").append(nowUtc).append("\r\n");
            sb.append("DTSTART;VALUE=DATE:").append(DATE_FMT.format(l.startDate())).append("\r\n");
            sb.append("DTEND;VALUE=DATE:").append(DATE_FMT.format(l.endDate().plusDays(1))).append("\r\n");
            sb.append("SUMMARY:Cuti (").append(escapeText(l.leaveType())).append("): ").append(escapeText(l.reason() != null ? l.reason() : "")).append("\r\n");
            sb.append("DESCRIPTION:Pengajuan cuti yang telah disetujui (").append(escapeText(l.portion())).append(")\\nAlasan: ").append(escapeText(l.reason() != null ? l.reason() : "")).append("\r\n");
            sb.append("STATUS:CONFIRMED\r\n");
            sb.append("TRANSP:OPAQUE\r\n");
            sb.append("END:VEVENT\r\n");
        }

        sb.append("END:VCALENDAR\r\n");
        return sb.toString();
    }

    private String formatFeedUrl(String baseUrl, String token) {
        String base = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl : "";
        if (base.endsWith("/")) base = base.substring(0, base.length() - 1);
        return base + "/api/v1/calendar/feed/" + token + ".ics";
    }

    private String escapeText(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                   .replace(";", "\\;")
                   .replace(",", "\\,")
                   .replace("\r\n", "\\n")
                   .replace("\n", "\\n");
    }

    private record TokenRow(UUID id, UUID userId) {}
    private record AllocEvent(UUID id, String projectName, LocalDate startDate, LocalDate endDate, BigDecimal hoursPerDay, String notes) {}
    private record LeaveEvent(UUID id, String leaveType, String portion, String reason, LocalDate startDate, LocalDate endDate) {}
}

