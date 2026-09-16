package cn.huanhuan.core;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class WorkbookProcessorTest {
    @TempDir Path directory;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-15T06:30:25Z"), ZoneId.of("Asia/Shanghai"));

    private Path writeFixture(java.util.function.Consumer<XSSFWorkbook> edit) throws IOException {
        Path input = directory.resolve("input.xlsx");
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            XSSFSheet sheet = wb.createSheet("ag-grid");
            XSSFRow header = sheet.createRow(0);
            for (int c = 0; c < 25; c++) header.createCell(c).setCellValue("字段" + c);
            header.getCell(0).setCellValue("序号");
            for (Column column : Column.values()) header.getCell(column.index()).setCellValue(column.title());
            addRow(sheet, 1, "2026/8/1 12:00:00", "2026/8/3 12:00:00");
            edit.accept(wb);
            try (OutputStream output = Files.newOutputStream(input)) { wb.write(output); }
        }
        return input;
    }

    private static XSSFRow addRow(XSSFSheet sheet, int index, String admissionCreated, String dischargeCreated) {
        XSSFRow row = sheet.createRow(index);
        for (int c = 0; c < 25; c++) row.createCell(c);
        row.getCell(0).setCellValue(900 + index);
        row.getCell(1).setCellValue("原始记录" + index);
        row.getCell(8).setCellValue("2026/8/3 0:00:00");
        row.getCell(9).setCellValue("2026/8/1 0:00:00");
        row.getCell(12).setCellValue("出院科室B");
        row.getCell(13).setCellValue("入院科室A");
        if (admissionCreated != null) row.getCell(16).setCellValue(admissionCreated);
        row.getCell(17).setCellFormula("(Q" + (index + 1) + "-J" + (index + 1) + ")*24");
        if (dischargeCreated != null) row.getCell(18).setCellValue(dischargeCreated);
        row.getCell(19).setCellValue(999);
        row.getCell(24).setCellValue("否");
        return row;
    }

    private ProcessingResult process(Path input) throws Exception {
        return new WorkbookProcessor(CLOCK).process(input, directory, ProgressListener.NONE);
    }

    @Test void exportsOneWorkbookWithSortedRowsPreservedColorsAndIndependentStatistics() throws Exception {
        Path input = writeFixture(wb -> {
            XSSFSheet sheet = wb.getSheetAt(0);
            XSSFCellStyle blue = wb.createCellStyle();
            blue.setFillForegroundColor(IndexedColors.LIGHT_BLUE.getIndex());
            blue.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            sheet.getRow(1).getCell(1).setCellStyle(blue);
            addRow(sheet, 2, "2026/8/2 12:00:00", "2026/8/3 12:00:00");
            addRow(sheet, 3, "2026/8/2 12:00:00", "2026/8/4 12:00:00");
            addRow(sheet, 4, "2026/8/1 12:00:00", "2026/8/4 12:00:00");
            addRow(sheet, 5, "2026/7/31 12:00:00", "2026/8/2 12:00:00");
        });
        byte[] before = Files.readAllBytes(input);
        ProcessingResult result = process(input);
        assertEquals(5, result.recordCount());
        assertEquals(3, result.lateRecordCount());
        assertArrayEquals(before, Files.readAllBytes(input));
        assertEquals("时效统计_20260915143025.xlsx", result.outputFile().getFileName().toString());
        try (XSSFWorkbook wb = new XSSFWorkbook(Files.newInputStream(result.outputFile()))) {
            assertEquals(2, wb.getNumberOfSheets());
            XSSFSheet detail = wb.getSheetAt(0);
            int[] original = {3, 2, 4, 1, 5};
            for (int i = 1; i <= 5; i++) {
                XSSFRow row = detail.getRow(i);
                assertEquals(i, row.getCell(0).getNumericCellValue());
                assertEquals("原始记录" + original[i - 1], row.getCell(1).getStringCellValue());
                assertEquals("否", row.getCell(24).getStringCellValue()); // 不按是否命中过滤。
                assertEquals(CellType.NUMERIC, row.getCell(17).getCellType());
                assertEquals("0.000000000", row.getCell(17).getCellStyle().getDataFormatString());
                if (i <= 3) for (int c = 0; c < 25; c++)
                    assertEquals(IndexedColors.RED.getIndex(), row.getCell(c).getCellStyle().getFillForegroundColor());
            }
            assertEquals(IndexedColors.LIGHT_BLUE.getIndex(), detail.getRow(4).getCell(1).getCellStyle().getFillForegroundColor());
            assertEquals(-12, detail.getRow(5).getCell(17).getNumericCellValue());
            assertEquals(-12, detail.getRow(5).getCell(19).getNumericCellValue());
            XSSFSheet stats = wb.getSheet("科室统计");
            assertEquals("24小时完成率", stats.getRow(0).getCell(0).getStringCellValue());
            assertEquals(1, stats.getNumMergedRegions());
            assertEquals("入院科室A", stats.getRow(2).getCell(0).getStringCellValue());
            assertEquals(0.6, stats.getRow(2).getCell(1).getNumericCellValue(), 1e-12);
            assertEquals("\\", stats.getRow(2).getCell(2).getStringCellValue());
            assertEquals("\\", stats.getRow(3).getCell(1).getStringCellValue());
            assertEquals(0.6, stats.getRow(3).getCell(2).getNumericCellValue(), 1e-12);
            assertEquals("0.00%", stats.getRow(3).getCell(2).getCellStyle().getDataFormatString());
        }
        try (var paths = Files.list(directory)) { assertEquals(2, paths.count()); }
    }

    @ParameterizedTest @ValueSource(ints = {20, 21, 22, 23})
    void fillsBothCreationTimesFromFirstNonblankFallback(int fallback) throws Exception {
        Path input = writeFixture(wb -> {
            XSSFRow row = wb.getSheetAt(0).getRow(1);
            row.getCell(16).setBlank();
            row.getCell(18).setBlank();
            row.getCell(fallback).setCellValue("2026/8/1 6:30:15");
            if (fallback < 23) row.getCell(fallback + 1).setCellValue("2026/8/5 0:00:00");
        });
        ProcessingResult result = process(input);
        try (XSSFWorkbook wb = new XSSFWorkbook(Files.newInputStream(result.outputFile()))) {
            XSSFRow row = wb.getSheetAt(0).getRow(1);
            assertEquals("2026/8/1 6:30:15", row.getCell(16).getStringCellValue());
            assertEquals(row.getCell(16).getStringCellValue(), row.getCell(18).getStringCellValue());
            assertEquals(23415 / 3600.0, row.getCell(17).getNumericCellValue(), 1e-12);
            assertEquals(0, result.lateRecordCount());
        }
    }

    @Test void preservesExistingTimeAndUsesExactThreshold() throws Exception {
        Path input = writeFixture(wb -> {
            XSSFRow row = wb.getSheetAt(0).getRow(1);
            row.getCell(16).setCellValue("2026/8/2 0:00:00");
            row.getCell(18).setCellValue("2026/8/4 0:00:01");
            row.getCell(20).setCellValue("2026/8/9 0:00:00");
        });
        ProcessingResult result = process(input);
        assertEquals(0, result.departments().get(0).admissionFailed());
        assertEquals(1, result.departments().get(1).dischargeFailed());
        try (XSSFWorkbook wb = new XSSFWorkbook(Files.newInputStream(result.outputFile()))) {
            XSSFRow row = wb.getSheetAt(0).getRow(1);
            assertEquals("2026/8/2 0:00:00", row.getCell(16).getStringCellValue());
            assertEquals(24, row.getCell(17).getNumericCellValue());
            assertEquals("24.000277778", new DataFormatter(Locale.ROOT).formatCellValue(row.getCell(19)));
        }
    }

    @ParameterizedTest @ValueSource(ints = {-43200, 0, 1800, 43200, 86399, 86400, 86401, 129600})
    void exportsHoursAndApplies24HourThresholdToBothIntervals(int seconds) throws Exception {
        Path input = writeFixture(wb -> {
            XSSFRow row = wb.getSheetAt(0).getRow(1);
            row.getCell(16).setCellValue(LocalDateTime.of(2026, 8, 1, 0, 0)
                    .plusSeconds(seconds).format(ExcelValues.TIME_FORMAT));
            row.getCell(18).setCellValue(LocalDateTime.of(2026, 8, 3, 0, 0)
                    .plusSeconds(seconds).format(ExcelValues.TIME_FORMAT));
        });
        ProcessingResult result = process(input);
        boolean late = seconds > 24 * 3600;
        assertEquals(late ? 1 : 0, result.lateRecordCount());
        assertEquals(late ? 1 : 0, result.departments().get(0).admissionFailed());
        assertEquals(late ? 1 : 0, result.departments().get(1).dischargeFailed());
        try (XSSFWorkbook wb = new XSSFWorkbook(Files.newInputStream(result.outputFile()))) {
            XSSFRow row = wb.getSheetAt(0).getRow(1);
            for (int column : new int[]{17, 19}) {
                assertEquals("间隔（小时）", wb.getSheetAt(0).getRow(0).getCell(column).getStringCellValue());
                assertEquals(seconds / 3600.0, row.getCell(column).getNumericCellValue(), 1e-12);
                assertEquals("0.000000000", row.getCell(column).getCellStyle().getDataFormatString());
            }
            for (Cell cell : row) {
                assertEquals(late, cell.getCellStyle().getFillForegroundColor() == IndexedColors.RED.getIndex());
            }
            XSSFSheet stats = wb.getSheet("科室统计");
            assertEquals(late ? 0 : 1, stats.getRow(2).getCell(1).getNumericCellValue());
            assertEquals(late ? 0 : 1, stats.getRow(3).getCell(2).getNumericCellValue());
        }
    }

    @ParameterizedTest @ValueSource(ints = {8, 9, 12, 13, 16, 18})
    void missingRequiredDataStopsAtOriginalRowWithoutOutput(int column) throws Exception {
        Path input = writeFixture(wb -> {
            XSSFRow bad = addRow(wb.getSheetAt(0), 2, "2026/8/2 1:00:00", "2026/8/4 1:00:00");
            bad.getCell(column).setBlank();
        });
        DataValidationException error = assertThrows(DataValidationException.class, () -> process(input));
        assertEquals(3, error.rowNumber());
        assertTrue(error.getMessage().contains("数据不合法：第 3 行"));
        try (var files = Files.list(directory)) { assertEquals(List.of(input), files.toList()); }
    }

    @ParameterizedTest @ValueSource(strings = {"2026/2/30 1:00:00", "不是时间", "2026/8/1 25:00:00", "2026-08-01"})
    void rejectsMalformedDates(String date) throws Exception {
        Path input = writeFixture(wb -> wb.getSheetAt(0).getRow(1).getCell(16).setCellValue(date));
        DataValidationException error = assertThrows(DataValidationException.class, () -> process(input));
        assertTrue(error.getMessage().contains("Q 列"));
        assertTrue(error.getMessage().contains("时间无法解析"));
    }

    @Test void invalidPreferredFallbackIsNotSkipped() throws Exception {
        Path input = writeFixture(wb -> {
            XSSFRow row = wb.getSheetAt(0).getRow(1);
            row.getCell(16).setBlank();
            row.getCell(20).setCellValue("错误时间");
            row.getCell(21).setCellValue("2026/8/1 12:00:00");
        });
        assertTrue(assertThrows(DataValidationException.class, () -> process(input)).getMessage().contains("U 列"));
    }

    @Test void duplicatesAreCountedAndBlankDepartmentIsRejected() throws Exception {
        Path input = writeFixture(wb -> {
            XSSFRow second = addRow(wb.getSheetAt(0), 2, "2026/8/1 12:00:00", "2026/8/3 12:00:00");
            second.getCell(1).setCellValue("原始记录1");
        });
        assertEquals(2, process(input).departments().get(0).admissionTotal());
        Path invalid = writeFixture(wb -> wb.getSheetAt(0).getRow(1).getCell(13).setCellValue("   "));
        assertThrows(DataValidationException.class, () -> process(invalid));
    }

    @Test void retainsExistingFilesOnNameCollision() throws Exception {
        Path input = writeFixture(wb -> {});
        Path occupied = directory.resolve("时效统计_20260915143025.xlsx");
        Files.writeString(occupied, "existing content");
        ProcessingResult first = process(input);
        ProcessingResult second = process(input);
        assertEquals("时效统计_20260915143025_1.xlsx", first.outputFile().getFileName().toString());
        assertEquals("时效统计_20260915143025_2.xlsx", second.outputFile().getFileName().toString());
        assertEquals("existing content", Files.readString(occupied));
    }

    @Test void validatesHeaderAndRejectsEmptyWorkbook() throws Exception {
        Path invalid = writeFixture(wb -> wb.getSheetAt(0).getRow(0).getCell(9).setCellValue("错误表头"));
        assertEquals(1, assertThrows(DataValidationException.class, () -> process(invalid)).rowNumber());
        Path empty = writeFixture(wb -> wb.getSheetAt(0).removeRow(wb.getSheetAt(0).getRow(1)));
        assertEquals(2, assertThrows(DataValidationException.class, () -> process(empty)).rowNumber());
    }

    @Test void originalStatisticsNameDoesNotOverwriteInputSheet() throws Exception {
        Path input = writeFixture(wb -> wb.setSheetName(0, "科室统计"));
        ProcessingResult result = process(input);
        try (XSSFWorkbook wb = new XSSFWorkbook(Files.newInputStream(result.outputFile()))) {
            assertEquals("科室统计", wb.getSheetName(0));
            assertEquals("科室统计_1", wb.getSheetName(1));
        }
    }
}
