package com.toppaload.leave.internal;

import com.toppaload.leave.HolidaySummary;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/holidays")
class HolidayController {

    private final HolidayService holidayService;

    HolidayController(HolidayService holidayService) {
        this.holidayService = holidayService;
    }

    @GetMapping
    List<HolidaySummary> listHolidays(@RequestParam(required = false) Integer year) {
        return holidayService.listHolidays(year);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR')")
    HolidaySummary createHoliday(@Valid @RequestBody HolidayService.CreateHolidayRequest request) {
        return holidayService.createOrUpdateHoliday(request);
    }

    @PostMapping(value = "/import", consumes = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR')")
    HolidayService.ImportResult importHolidaysJson(@RequestBody List<@Valid HolidayService.HolidayImportItem> items) {
        return holidayService.importHolidays(items);
    }

    @PostMapping(value = "/import", consumes = {"text/csv", "text/plain", "application/csv"})
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR')")
    HolidayService.ImportResult importHolidaysCsv(@RequestBody String csvContent) {
        return holidayService.importHolidaysFromCsv(csvContent);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR')")
    void deleteHoliday(@PathVariable UUID id) {
        holidayService.deleteHoliday(id);
    }
}
