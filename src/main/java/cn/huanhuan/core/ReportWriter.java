package cn.huanhuan.core;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.*;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/** 整个工作簿完成序列化后才发布；保存错误时清理临时文件。 */
final class ReportWriter {
    // Java 的秒格式是 ss；SS 是小数秒，不符合用户要求的年月日时分秒。
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    Path write(XSSFWorkbook workbook, List<DepartmentStatistics> departments, Path directory, Clock clock)
            throws IOException {
        String stamp = LocalDateTime.now(clock).format(STAMP);
        Path temporary = null;
        addStatisticsSheet(workbook, departments);
        try {
            temporary = Files.createTempFile(directory, ".huanhuan-", ".tmp");
            try (OutputStream out = Files.newOutputStream(temporary)) { workbook.write(out); }
            return publish(temporary, directory, "时效统计_" + stamp);
        } finally {
            remove(temporary);
        }
    }

    private static void remove(Path path) {
        if (path != null) {
            try { Files.deleteIfExists(path); } catch (IOException ignored) { /* 系统锁定时留给用户处理 */ }
        }
    }

    private static Path publish(Path temporary, Path directory, String name) throws IOException {
        for (int suffix = 0; ; suffix++) {
            Path destination = directory.resolve(name + (suffix == 0 ? "" : "_" + suffix) + ".xlsx");
            try {
                // 不使用 REPLACE_EXISTING，竞争创建的同名文件也必须保留。
                return Files.move(temporary, destination);
            } catch (FileAlreadyExistsException ignored) { /* 继续尝试下一个序号 */ }
        }
    }

    static void addStatisticsSheet(XSSFWorkbook workbook, List<DepartmentStatistics> departments) {
        String name = "科室统计";
        for (int i = 1; workbook.getSheet(name) != null; i++) name = "科室统计_" + i;
        Sheet sheet = workbook.createSheet(name);
        sheet.setColumnWidth(0, 32 * 256);
        sheet.setColumnWidth(1, 20 * 256);
        sheet.setColumnWidth(2, 20 * 256);
        sheet.createFreezePane(1, 2);
        CellStyle body = workbook.createCellStyle();
        body.setAlignment(HorizontalAlignment.CENTER);
        body.setVerticalAlignment(VerticalAlignment.CENTER);
        body.setBorderTop(BorderStyle.THIN);
        body.setBorderBottom(BorderStyle.THIN);
        body.setBorderLeft(BorderStyle.THIN);
        body.setBorderRight(BorderStyle.THIN);
        Font font = workbook.createFont();
        font.setFontName("宋体");
        font.setFontHeightInPoints((short) 12);
        body.setFont(font);
        CellStyle heading = workbook.createCellStyle();
        heading.cloneStyleFrom(body);
        Font bold = workbook.createFont();
        bold.setFontName("宋体");
        bold.setFontHeightInPoints((short) 12);
        bold.setBold(true);
        heading.setFont(bold);
        CellStyle percent = workbook.createCellStyle();
        percent.cloneStyleFrom(body);
        percent.setDataFormat(workbook.createDataFormat().getFormat("0.00%"));

        Row title = sheet.createRow(0);
        title.setHeightInPoints(30);
        for (int c = 0; c < 3; c++) title.createCell(c).setCellStyle(heading);
        title.getCell(0).setCellValue("24小时完成率");
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 2));
        Row header = sheet.createRow(1);
        header.setHeightInPoints(28);
        String[] labels = {"科室", "入院", "出院"};
        for (int c = 0; c < labels.length; c++) {
            Cell cell = header.createCell(c);
            cell.setCellStyle(heading);
            cell.setCellValue(labels[c]);
        }
        for (int i = 0; i < departments.size(); i++) {
            DepartmentStatistics stats = departments.get(i);
            Row row = sheet.createRow(i + 2);
            row.setHeightInPoints(25);
            row.createCell(0).setCellValue(stats.name());
            row.getCell(0).setCellStyle(body);
            Cell admission = row.createCell(1);
            admission.setCellStyle(percent);
            if (stats.admissionTotal() == 0) admission.setCellValue("\\");
            else admission.setCellValue(stats.admissionRate());
            Cell discharge = row.createCell(2);
            discharge.setCellStyle(percent);
            if (stats.dischargeTotal() == 0) discharge.setCellValue("\\");
            else discharge.setCellValue(stats.dischargeRate());
        }
        sheet.setRepeatingRows(new CellRangeAddress(0, 1, -1, -1));
        sheet.setFitToPage(true);
        sheet.getPrintSetup().setFitWidth((short) 1);
        sheet.getPrintSetup().setFitHeight((short) 0);
    }
}
