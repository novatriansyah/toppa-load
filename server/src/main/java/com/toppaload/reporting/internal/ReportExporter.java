package com.toppaload.reporting.internal;

import com.toppaload.reporting.MemberUtilizationDto;
import com.toppaload.reporting.UtilizationSummaryDto;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

public final class ReportExporter {

    private ReportExporter() {}

    public static byte[] exportCsv(UtilizationSummaryDto summary) {
        StringBuilder sb = new StringBuilder();
        // UTF-8 BOM for Microsoft Excel compatibility
        sb.append('\uFEFF');
        sb.append("Laporan Utilisasi Tim Toppa Load\n");
        sb.append("Periode,").append(summary.startDate()).append(" s/d ").append(summary.endDate()).append("\n");
        sb.append("Total Kapasitas (Jam),").append(summary.totalCapacityHours()).append("\n");
        sb.append("Total Alokasi (Jam),").append(summary.totalAllocatedHours()).append("\n");
        sb.append("Total Aktual Timesheet (Jam),").append(summary.totalActualHours()).append("\n");
        sb.append("Rata-rata Utilisasi (%),").append(summary.overallUtilizationPct()).append("%\n");
        sb.append("Anggota Overload,").append(summary.overloadedMembersCount()).append("\n\n");

        sb.append("Nama Karyawan,Email,Role,Kapasitas Mingguan (Jam),Kapasitas Bersih (Jam),Alokasi Proyek (Jam),Timesheet Aktual (Jam),Utilisasi (%),Status Beban Kerja,Minggu Overload\n");

        for (MemberUtilizationDto m : summary.members()) {
            sb.append(escapeCsv(m.name())).append(",")
              .append(escapeCsv(m.email())).append(",")
              .append(escapeCsv(m.role())).append(",")
              .append(m.weeklyCapacityHours()).append(",")
              .append(m.netCapacityHours()).append(",")
              .append(m.allocatedHours()).append(",")
              .append(m.timesheetHours()).append(",")
              .append(m.utilizationPct()).append("%,")
              .append(escapeCsv(m.status())).append(",")
              .append(m.overloadWeekCount()).append("\n");
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] exportXlsx(UtilizationSummaryDto summary) {
        try (XSSFWorkbook workbook = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Laporan Utilisasi");

            // Fonts & Styles
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);

            CellStyle titleStyle = workbook.createCellStyle();
            titleStyle.setFont(titleFont);

            Font boldFont = workbook.createFont();
            boldFont.setBold(true);

            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(boldFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderBottom(BorderStyle.THIN);
            headerStyle.setBorderTop(BorderStyle.THIN);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);

            CellStyle borderStyle = workbook.createCellStyle();
            borderStyle.setBorderBottom(BorderStyle.THIN);
            borderStyle.setBorderTop(BorderStyle.THIN);
            borderStyle.setBorderLeft(BorderStyle.THIN);
            borderStyle.setBorderRight(BorderStyle.THIN);

            CellStyle numStyle = workbook.createCellStyle();
            numStyle.cloneStyleFrom(borderStyle);
            numStyle.setAlignment(HorizontalAlignment.RIGHT);

            // Title block
            int rowIdx = 0;
            Row titleRow = sheet.createRow(rowIdx++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("Toppa Load - Laporan Utilisasi & Kapasitas Tim");
            titleCell.setCellStyle(titleStyle);

            Row periodRow = sheet.createRow(rowIdx++);
            periodRow.createCell(0).setCellValue("Periode: " + summary.startDate() + " s/d " + summary.endDate());

            Row statsRow = sheet.createRow(rowIdx++);
            statsRow.createCell(0).setCellValue(String.format("Utilisasi Tim: %s%% | Total Alokasi: %s jam | Total Timesheet: %s jam",
                    summary.overallUtilizationPct(), summary.totalAllocatedHours(), summary.totalActualHours()));
            rowIdx++; // blank line

            // Table Header
            String[] headers = {
                    "Nama Karyawan", "Email", "Role", "Kapasitas (Jam)",
                    "Alokasi Proyek (Jam)", "Timesheet (Jam)", "Utilisasi (%)", "Status Beban Kerja", "Minggu Overload"
            };

            Row headerRow = sheet.createRow(rowIdx++);
            for (int i = 0; i < headers.length; i++) {
                Cell c = headerRow.createCell(i);
                c.setCellValue(headers[i]);
                c.setCellStyle(headerStyle);
            }

            // Data Rows
            for (MemberUtilizationDto m : summary.members()) {
                Row row = sheet.createRow(rowIdx++);

                Cell c0 = row.createCell(0); c0.setCellValue(m.name()); c0.setCellStyle(borderStyle);
                Cell c1 = row.createCell(1); c1.setCellValue(m.email()); c1.setCellStyle(borderStyle);
                Cell c2 = row.createCell(2); c2.setCellValue(m.role()); c2.setCellStyle(borderStyle);
                Cell c3 = row.createCell(3); c3.setCellValue(m.netCapacityHours().doubleValue()); c3.setCellStyle(numStyle);
                Cell c4 = row.createCell(4); c4.setCellValue(m.allocatedHours().doubleValue()); c4.setCellStyle(numStyle);
                Cell c5 = row.createCell(5); c5.setCellValue(m.timesheetHours().doubleValue()); c5.setCellStyle(numStyle);
                Cell c6 = row.createCell(6); c6.setCellValue(m.utilizationPct().doubleValue()); c6.setCellStyle(numStyle);
                Cell c7 = row.createCell(7); c7.setCellValue(m.status()); c7.setCellStyle(borderStyle);
                Cell c8 = row.createCell(8); c8.setCellValue(m.overloadWeekCount()); c8.setCellStyle(numStyle);
            }

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Gagal meng-generate file Excel: " + e.getMessage(), e);
        }
    }

    private static String escapeCsv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}

