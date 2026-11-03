package com.toppaload.reporting.internal;

import com.toppaload.reporting.ReportingService;
import com.toppaload.reporting.UtilizationSummaryDto;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/reports")
class ReportingController {

    private final ReportingService reportingService;

    ReportingController(ReportingService reportingService) {
        this.reportingService = reportingService;
    }

    @GetMapping("/utilization")
    public ResponseEntity<UtilizationSummaryDto> getUtilization(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ResponseEntity.ok(reportingService.getUtilizationSummary(startDate, endDate));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportReport(
            @RequestParam(defaultValue = "csv") String format,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        byte[] data = reportingService.exportReport(format, startDate, endDate);
        String dateSuffix = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);

        HttpHeaders headers = new HttpHeaders();
        if ("xlsx".equalsIgnoreCase(format)) {
            headers.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
            headers.setContentDispositionFormData("attachment", "utilization-report-" + dateSuffix + ".xlsx");
        } else {
            headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
            headers.setContentDispositionFormData("attachment", "utilization-report-" + dateSuffix + ".csv");
        }

        return ResponseEntity.ok().headers(headers).body(data);
    }

    @PostMapping("/send-summary")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR')")
    public ResponseEntity<Map<String, String>> triggerWeeklySummary() {
        reportingService.sendWeeklyUtilizationEmail();
        return ResponseEntity.ok(Map.of("message", "Email rekap mingguan berhasil dikirim atau dicatat."));
    }
}

