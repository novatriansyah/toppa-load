package com.toppaload.leave.internal;

import com.toppaload.audit.AuditRecorder;
import com.toppaload.identity.AuthenticatedActor;
import com.toppaload.identity.UserDirectory;
import com.toppaload.leave.HolidayScope;
import com.toppaload.leave.HolidaySummary;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional
class HolidayService {

    record CreateHolidayRequest(
            @NotNull(message = "Tanggal libur wajib diisi")
            LocalDate holidayDate,

            @NotBlank(message = "Nama hari libur wajib diisi")
            @Size(max = 160, message = "Nama hari libur maksimal 160 karakter")
            String name,

            HolidayScope scope
    ) {}

    record HolidayImportItem(
            @NotNull
            LocalDate holidayDate,

            @NotBlank
            String name,

            HolidayScope scope
    ) {}

    record ImportResult(int imported, int count) {}

    private final HolidayRepository holidayRepository;
    private final AuditRecorder auditRecorder;
    private final UserDirectory userDirectory;

    HolidayService(HolidayRepository holidayRepository, AuditRecorder auditRecorder, UserDirectory userDirectory) {
        this.holidayRepository = holidayRepository;
        this.auditRecorder = auditRecorder;
        this.userDirectory = userDirectory;
    }

    @Transactional(readOnly = true)
    List<HolidaySummary> listHolidays(Integer year) {
        List<HolidayEntity> entities;
        if (year != null) {
            LocalDate start = LocalDate.of(year, 1, 1);
            LocalDate end = LocalDate.of(year, 12, 31);
            entities = holidayRepository.findByHolidayDateBetweenAndDeletedAtIsNullOrderByHolidayDateAsc(start, end);
        } else {
            entities = holidayRepository.findByDeletedAtIsNullOrderByHolidayDateAsc();
        }
        return entities.stream().map(this::toSummary).toList();
    }

    HolidaySummary createOrUpdateHoliday(CreateHolidayRequest request) {
        HolidayScope scope = request.scope() != null ? request.scope() : HolidayScope.NATIONAL;
        String name = request.name().trim();

        Optional<HolidayEntity> existingActive = holidayRepository.findByHolidayDateAndScopeAndDeletedAtIsNull(request.holidayDate(), scope);
        AuthenticatedActor actor = userDirectory.requireCurrentActor();

        if (existingActive.isPresent()) {
            HolidayEntity entity = existingActive.get();
            Map<String, Object> beforeData = toSnapshot(entity);
            entity.setName(name);
            entity.setUpdatedAt(Instant.now());
            HolidayEntity saved = holidayRepository.save(entity);

            auditRecorder.record(
                    actor.userId(),
                    actor.email(),
                    "UPDATE",
                    "HOLIDAY",
                    saved.getId().toString(),
                    beforeData,
                    toSnapshot(saved)
            );
            return toSummary(saved);
        }

        // Check if soft-deleted entry exists
        Optional<HolidayEntity> existingAny = holidayRepository.findFirstByHolidayDateAndScopeOrderByCreatedAtDesc(request.holidayDate(), scope);
        if (existingAny.isPresent()) {
            HolidayEntity entity = existingAny.get();
            Map<String, Object> beforeData = toSnapshot(entity);
            entity.setName(name);
            entity.setDeletedAt(null);
            entity.setUpdatedAt(Instant.now());
            HolidayEntity saved = holidayRepository.save(entity);

            auditRecorder.record(
                    actor.userId(),
                    actor.email(),
                    "UPDATE",
                    "HOLIDAY",
                    saved.getId().toString(),
                    beforeData,
                    toSnapshot(saved)
            );
            return toSummary(saved);
        }

        HolidayEntity entity = new HolidayEntity(request.holidayDate(), name, scope);
        HolidayEntity saved = holidayRepository.save(entity);

        auditRecorder.record(
                actor.userId(),
                actor.email(),
                "CREATE",
                "HOLIDAY",
                saved.getId().toString(),
                null,
                toSnapshot(saved)
        );
        return toSummary(saved);
    }

    ImportResult importHolidays(List<HolidayImportItem> items) {
        if (items == null || items.isEmpty()) {
            return new ImportResult(0, 0);
        }

        int count = 0;
        for (HolidayImportItem item : items) {
            if (item.holidayDate() == null || item.name() == null || item.name().isBlank()) {
                continue;
            }
            HolidayScope scope = item.scope() != null ? item.scope() : HolidayScope.NATIONAL;
            String name = item.name().trim();

            Optional<HolidayEntity> existingActive = holidayRepository.findByHolidayDateAndScopeAndDeletedAtIsNull(item.holidayDate(), scope);
            if (existingActive.isPresent()) {
                HolidayEntity entity = existingActive.get();
                entity.setName(name);
                entity.setUpdatedAt(Instant.now());
                holidayRepository.save(entity);
            } else {
                Optional<HolidayEntity> existingAny = holidayRepository.findFirstByHolidayDateAndScopeOrderByCreatedAtDesc(item.holidayDate(), scope);
                if (existingAny.isPresent()) {
                    HolidayEntity entity = existingAny.get();
                    entity.setName(name);
                    entity.setDeletedAt(null);
                    entity.setUpdatedAt(Instant.now());
                    holidayRepository.save(entity);
                } else {
                    HolidayEntity entity = new HolidayEntity(item.holidayDate(), name, scope);
                    holidayRepository.save(entity);
                }
            }
            count++;
        }

        AuthenticatedActor actor = userDirectory.requireCurrentActor();
        auditRecorder.record(
                actor.userId(),
                actor.email(),
                "IMPORT",
                "HOLIDAY",
                "batch",
                null,
                Map.of("importedCount", count)
        );

        return new ImportResult(count, count);
    }

    ImportResult importHolidaysFromCsv(String csvContent) {
        if (csvContent == null || csvContent.isBlank()) {
            return new ImportResult(0, 0);
        }

        List<HolidayImportItem> items = new ArrayList<>();
        String[] lines = csvContent.split("\\r?\\n");
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.toLowerCase().startsWith("tanggal") || line.toLowerCase().startsWith("date")) {
                continue;
            }

            List<String> tokens = parseCsvLine(line);
            if (tokens.size() < 2) {
                continue;
            }

            String dateStr = cleanToken(tokens.get(0));
            String name = cleanToken(tokens.get(1));
            String scopeStr = tokens.size() > 2 ? cleanToken(tokens.get(2)) : null;

            LocalDate holidayDate;
            try {
                holidayDate = LocalDate.parse(dateStr);
            } catch (Exception e) {
                continue;
            }

            HolidayScope scope = HolidayScope.fromString(scopeStr);
            items.add(new HolidayImportItem(holidayDate, name, scope));
        }

        return importHolidays(items);
    }

    void deleteHoliday(UUID id) {
        HolidayEntity entity = holidayRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Hari libur tidak ditemukan: " + id));

        Map<String, Object> beforeData = toSnapshot(entity);
        entity.setDeletedAt(Instant.now());
        holidayRepository.save(entity);

        AuthenticatedActor actor = userDirectory.requireCurrentActor();
        auditRecorder.record(
                actor.userId(),
                actor.email(),
                "DELETE",
                "HOLIDAY",
                entity.getId().toString(),
                beforeData,
                null
        );
    }

    private HolidaySummary toSummary(HolidayEntity entity) {
        return new HolidaySummary(
                entity.getId(),
                entity.getHolidayDate(),
                entity.getName(),
                entity.getScope(),
                entity.getCreatedAt()
        );
    }

    private Map<String, Object> toSnapshot(HolidayEntity entity) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", entity.getId() != null ? entity.getId().toString() : null);
        map.put("holidayDate", entity.getHolidayDate() != null ? entity.getHolidayDate().toString() : null);
        map.put("name", entity.getName());
        map.put("scope", entity.getScope() != null ? entity.getScope().name() : null);
        map.put("deletedAt", entity.getDeletedAt() != null ? entity.getDeletedAt().toString() : null);
        return map;
    }

    private List<String> parseCsvLine(String line) {
        List<String> tokens = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                tokens.add(sb.toString().trim());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        tokens.add(sb.toString().trim());
        return tokens;
    }

    private String cleanToken(String token) {
        if (token == null) {
            return "";
        }
        String trimmed = token.trim();
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length() >= 2) {
            return trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed;
    }
}
