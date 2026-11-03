package com.toppaload.allocation.internal;

import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/allocations")
class AllocationCsvImportController {

    private final AllocationCsvImportService importService;

    AllocationCsvImportController(AllocationCsvImportService importService) {
        this.importService = importService;
    }

    @PostMapping(value = "/import-csv", consumes = {"text/csv", "text/plain", "application/csv"})
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERVISOR')")
    AllocationCsvImportService.CsvImportResult importCsvText(@RequestBody String csvContent) {
        return importService.importAllocationsCsv(csvContent);
    }

    @PostMapping(value = "/import-csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ADMIN') or hasRole('SUPERVISOR')")
    AllocationCsvImportService.CsvImportResult importCsvFile(@RequestParam("file") MultipartFile file) throws IOException {
        String content = new String(file.getBytes(), StandardCharsets.UTF_8);
        return importService.importAllocationsCsv(content);
    }
}

