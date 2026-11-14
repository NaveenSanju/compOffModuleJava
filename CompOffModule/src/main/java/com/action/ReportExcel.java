package com.action;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Excel (.xlsx) files of the admin reports, built with Apache POI from the same rows the report page shows.
 * Column widths are fixed (autoSizeColumn needs AWT fonts, which App Engine does not have).
 */
public final class ReportExcel {

    private static final String[] USAGE_HEADERS = {"Team name", "Staff name", "Employee code", "Holiday worked date",
        "Usage status", "Comp Off date", "Day type", "Comp Off requested on", "Comp Off status"};
    private static final int[] USAGE_WIDTHS = {18, 24, 14, 18, 14, 16, 10, 20, 16};

    private ReportExcel() {
    }

    /** Holiday Intimation Usage Report rows (ReportManager.holidayUsage) as an .xlsx file. */
    public static byte[] holidayUsage(List<Map<String, Object>> rows, String filterText, LocalDate today) {
        XSSFWorkbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("Holiday Intimation Usage");
        CellStyle title = style(wb, true, false, null), header = style(wb, true, true, null),
                text = style(wb, false, true, null), date = style(wb, false, true, "dd mmm yyyy");
        header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        header.setFillPattern(CellStyle.SOLID_FOREGROUND);

        cell(sheet.createRow(0), 0, "Holiday Intimation Usage Report", title);
        cell(sheet.createRow(1), 0, filterText + "  |  Generated on " + today.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
                + "  |  " + rows.size() + " record" + (rows.size() == 1 ? "" : "s"), null);
        Row head = sheet.createRow(3);
        for (int i = 0; i < USAGE_HEADERS.length; i++) {
            cell(head, i, USAGE_HEADERS[i], header);
            sheet.setColumnWidth(i, USAGE_WIDTHS[i] * 256);
        }
        int r = 4;
        for (Map<String, Object> m : rows) {
            Row row = sheet.createRow(r++);
            boolean used = m.get("compOffId") != null;
            cell(row, 0, m.get("teamName"), text);
            cell(row, 1, m.get("staffName"), text);
            cell(row, 2, m.get("employeeCode"), text);
            cell(row, 3, m.get("workedDate"), date);
            cell(row, 4, m.get("usage"), text);
            cell(row, 5, used ? m.get("leaveDate") : null, date);
            cell(row, 6, used ? m.get("dayType") : null, text);
            cell(row, 7, used ? m.get("requestedDate") : null, date);
            cell(row, 8, used ? m.get("compOffStatus") : "Not used yet", text);
        }
        if (rows.isEmpty()) {
            cell(sheet.createRow(r), 0, "No record found", text);
        }
        sheet.createFreezePane(0, 4);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            wb.write(out);
        } catch (IOException e) {
            throw new IllegalStateException("Could not build the Excel file", e);
        }
        return out.toByteArray();
    }

    private static final String[] BALANCE_HEADERS = {"Name", "Team Name", "Employee Code", "Date of Joining (DOJ)",
        "Holiday Worked Days", "Comp Off Used", "Comp Off Balance"};
    private static final int[] BALANCE_WIDTHS = {24, 18, 14, 20, 18, 14, 16};
    private static final String[] STAFF_DETAIL_HEADERS = {"Staff Name", "Employee Code", "Holiday Worked Date", "Comp Off Date", "Notes",
        "Staff notes"};
    private static final int[] STAFF_DETAIL_WIDTHS = {24, 14, 18, 16, 62, 34};

    /** Comp Off Balance and Details Report rows (ReportManager.compOffBalance) as an .xlsx file. */
    public static byte[] compOffBalance(List<Map<String, Object>> rows, LocalDate today) {
        XSSFWorkbook wb = new XSSFWorkbook();
        Styles st = new Styles(wb);
        Sheet sheet = wb.createSheet("Comp Off Balance and Details");
        int r = header(sheet, st, "Comp Off Balance and Details Report", generated(today, rows.size()), BALANCE_HEADERS, BALANCE_WIDTHS, 3);
        for (Map<String, Object> m : rows) {
            Row row = sheet.createRow(r++);
            cell(row, 0, m.get("staffName"), st.text);
            cell(row, 1, m.get("teamName"), st.text);
            cell(row, 2, m.get("employeeCode"), st.text);
            cell(row, 3, m.get("dateOfJoining"), st.date);
            number(row, 4, m.get("allocated"), st.number);
            number(row, 5, m.get("used"), st.number);
            number(row, 6, m.get("balance"), st.number);
        }
        return finish(wb, sheet, rows.isEmpty(), r, 4, st);
    }

    /** View Details of one staff member (ReportManager.staffCompOffDetails), with the staff member's figures on top. */
    public static byte[] staffCompOffDetails(Map<String, Object> staff, List<Map<String, Object>> rows, LocalDate today) {
        XSSFWorkbook wb = new XSSFWorkbook();
        Styles st = new Styles(wb);
        Sheet sheet = wb.createSheet("Staff Comp Off Details");
        String sub = "Team Name: " + dash(staff.get("teamName")) + "  |  DOJ: " + dash(staff.get("dateOfJoining") instanceof Date
                ? com.util.DateUtil.display((Date) staff.get("dateOfJoining")) : null)
                + "  |  Holiday Worked Days: " + CompOffManager.plainDays(decimal(staff.get("allocated")))
                + "  |  Comp Off Used: " + CompOffManager.plainDays(decimal(staff.get("used")))
                + "  |  Comp Off Balance: " + CompOffManager.plainDays(decimal(staff.get("balance")));
        cell(sheet.createRow(2), 0, sub, null);
        int r = header(sheet, st, "Staff Comp Off Details - " + staff.get("staffName") + " (" + staff.get("employeeCode") + ")",
                generated(today, rows.size()), STAFF_DETAIL_HEADERS, STAFF_DETAIL_WIDTHS, 4);
        for (Map<String, Object> m : rows) {
            Row row = sheet.createRow(r++);
            boolean applied = m.get("compOffDate") != null;
            cell(row, 0, m.get("staffName"), st.text);
            cell(row, 1, m.get("employeeCode"), st.text);
            cell(row, 2, m.get("workedDate"), st.date);
            cell(row, 3, m.get("compOffDate"), st.date);
            cell(row, 4, applied ? m.get("notes") : "Not Applied", st.text);
            cell(row, 5, m.get("staffNotes"), st.text);
        }
        return finish(wb, sheet, rows.isEmpty(), r, 5, st);
    }

    /** Cell styles of one workbook. */
    private static final class Styles {
        final CellStyle title, header, text, date, number;
        Styles(XSSFWorkbook wb) {
            title = style(wb, true, false, null);
            header = style(wb, true, true, null);
            header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            header.setFillPattern(CellStyle.SOLID_FOREGROUND);
            text = style(wb, false, true, null);
            date = style(wb, false, true, "dd mmm yyyy");
            number = style(wb, false, true, null);
            number.setAlignment(CellStyle.ALIGN_CENTER);
        }
    }

    /** Title (row 0), subtitle (row 1) and the column headers at headerRow; returns the first data row. */
    private static int header(Sheet sheet, Styles st, String title, String subtitle, String[] headers, int[] widths, int headerRow) {
        cell(sheet.createRow(0), 0, title, st.title);
        cell(sheet.createRow(1), 0, subtitle, null);
        Row head = sheet.createRow(headerRow);
        for (int i = 0; i < headers.length; i++) {
            cell(head, i, headers[i], st.header);
            sheet.setColumnWidth(i, widths[i] * 256);
        }
        return headerRow + 1;
    }

    private static String generated(LocalDate today, int n) {
        return "Generated on " + today.format(DateTimeFormatter.ofPattern("dd MMM yyyy")) + "  |  " + n + " record" + (n == 1 ? "" : "s");
    }

    private static byte[] finish(XSSFWorkbook wb, Sheet sheet, boolean empty, int r, int freezeRow, Styles st) {
        if (empty) {
            cell(sheet.createRow(r), 0, "No record found", st.text);
        }
        sheet.createFreezePane(0, freezeRow);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            wb.write(out);
        } catch (IOException e) {
            throw new IllegalStateException("Could not build the Excel file", e);
        }
        return out.toByteArray();
    }

    /** A number cell (days, e.g. 1 or 0.5); null is written as 0. */
    private static void number(Row row, int col, Object value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(decimal(value).doubleValue());
        c.setCellStyle(style);
    }

    private static java.math.BigDecimal decimal(Object value) {
        return value instanceof java.math.BigDecimal ? (java.math.BigDecimal) value
                : value instanceof Number ? new java.math.BigDecimal(value.toString()) : java.math.BigDecimal.ZERO;
    }

    private static String dash(Object value) {
        return value == null || value.toString().trim().isEmpty() ? "-" : value.toString();
    }

    /** A text or date cell; null is written as "-". */
    private static void cell(Row row, int col, Object value, CellStyle style) {
        Cell c = row.createCell(col);
        if (value instanceof Date) {
            c.setCellValue((Date) value);
        } else {
            c.setCellValue(value == null ? "-" : value.toString());
        }
        if (style != null) {
            c.setCellStyle(style);
        }
    }

    private static CellStyle style(XSSFWorkbook wb, boolean bold, boolean border, String dateFormat) {
        CellStyle s = wb.createCellStyle();
        if (bold) {
            Font f = wb.createFont();
            f.setBoldweight(Font.BOLDWEIGHT_BOLD);
            s.setFont(f);
        }
        if (border) {
            s.setBorderTop(CellStyle.BORDER_THIN);
            s.setBorderBottom(CellStyle.BORDER_THIN);
            s.setBorderLeft(CellStyle.BORDER_THIN);
            s.setBorderRight(CellStyle.BORDER_THIN);
            s.setVerticalAlignment(CellStyle.VERTICAL_TOP);
        }
        if (dateFormat != null) {
            s.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat(dateFormat));
            s.setAlignment(CellStyle.ALIGN_LEFT);
        }
        return s;
    }
}
