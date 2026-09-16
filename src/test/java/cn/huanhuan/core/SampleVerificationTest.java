package cn.huanhuan.core;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.*;
import java.security.MessageDigest;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "sampleVerification", matches = "true")
class SampleVerificationTest {
    @Test void processesOriginalWorkbookWithoutChangingIt() throws Exception {
        Path input;
        try (var files = Files.list(Path.of("files"))) {
            input = files.filter(p -> p.toString().endsWith(".xlsx")).findFirst().orElseThrow();
        }
        byte[] original = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(input));
        Path output = Files.createDirectories(Path.of("target/sample-verification"));
        ProcessingResult result = new WorkbookProcessor().process(input, output, ProgressListener.NONE);
        assertArrayEquals(original, MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(input)));
        assertEquals(3413, result.recordCount());
        assertEquals(3413, result.departments().stream().mapToInt(DepartmentStatistics::admissionTotal).sum());
        assertEquals(3413, result.departments().stream().mapToInt(DepartmentStatistics::dischargeTotal).sum());
        try (XSSFWorkbook workbook = new XSSFWorkbook(Files.newInputStream(result.outputFile()))) {
            assertEquals(2, workbook.getNumberOfSheets());
            for (var row : workbook.getSheetAt(0)) {
                if (row.getRowNum() == 0) continue;
                assertEquals(CellType.NUMERIC, row.getCell(17).getCellType());
                assertEquals(CellType.NUMERIC, row.getCell(19).getCellType());
                for (Cell cell : row) assertNotEquals(CellType.ERROR, cell.getCellType());
            }
        }
        Files.writeString(output.resolve("result-path.txt"), result.outputFile().toAbsolutePath().toString());
        System.out.println("Sample verified: " + result.recordCount() + " rows; " + result.lateRecordCount()
                + " late rows; " + result.departments().size() + " departments.");
    }
}
