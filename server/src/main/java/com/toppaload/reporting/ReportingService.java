package com.toppaload.reporting;

import java.time.LocalDate;

public interface ReportingService {

    UtilizationSummaryDto getUtilizationSummary(LocalDate startDate, LocalDate endDate);

    byte[] exportReport(String format, LocalDate startDate, LocalDate endDate);

    void sendWeeklyUtilizationEmail();
}

