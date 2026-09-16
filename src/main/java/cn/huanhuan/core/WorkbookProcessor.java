package cn.huanhuan.core;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.time.*;
import java.util.*;

import static cn.huanhuan.core.Column.*;

/** 无 JavaFX 依赖的处理核心；只在所有记录合法后写出结果。 */
public final class WorkbookProcessor {
    private static final List<Column> FALLBACK = List.of(SHORT_STAY, SHORT_STAY_DEATH, DISCHARGE_SUMMARY, DEATH_RECORD);
    private static final Duration LIMIT = Duration.ofHours(24);
    private final Clock clock;

    public WorkbookProcessor() { this(Clock.systemDefaultZone()); }
    public WorkbookProcessor(Clock clock) { this.clock = Objects.requireNonNull(clock); }

    public ProcessingResult process(Path input, Path outputDirectory, ProgressListener progress)
            throws IOException, DataValidationException {
        Objects.requireNonNull(progress);
        if (!Files.isRegularFile(input) || !input.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".xlsx")) {
            throw new IOException("请选择可读取的 XLSX 文件。");
        }
        if (!Files.isDirectory(outputDirectory)) throw new IOException("保存目录不存在，请重新选择目录。");
        progress.update(0.02, "正在读取文件");
        try (InputStream stream = Files.newInputStream(input); XSSFWorkbook workbook = new XSSFWorkbook(stream)) {
            if (workbook.getNumberOfSheets() != 1) throw new DataValidationException(1, "文件应包含一个工作表。");
            XSSFSheet sheet = workbook.getSheetAt(0);
            validateHeaders(sheet);
            List<Record> records = validateRecords(sheet, progress);
            if (records.isEmpty()) throw new DataValidationException(2, "没有可处理的数据。");
            progress.update(0.45, "校验通过，正在计算与统计");
            List<DepartmentStatistics> statistics = summarize(records);
            applyResults(workbook, sheet, records);
            progress.update(0.66, "正在排序并标红超时记录");
            sortRows(workbook, sheet, records);
            progress.update(0.82, "正在生成科室统计工作表并保存");
            Path result = new ReportWriter().write(workbook, statistics, outputDirectory, clock);
            progress.update(1, "处理完成");
            return new ProcessingResult(result, records.size(),
                    (int) records.stream().filter(r -> r.lateCount() > 0).count(), statistics);
        }
    }

    private static void validateHeaders(XSSFSheet sheet) throws DataValidationException {
        XSSFRow header = sheet.getRow(0);
        for (Column column : Column.values()) {
            if (header == null || !column.title().equals(ExcelValues.text(header.getCell(column.index())))) {
                throw new DataValidationException(1, "表头不符合样例，应包含 " + column.label() + "。");
            }
        }
    }

    private static List<Record> validateRecords(XSSFSheet sheet, ProgressListener progress) throws DataValidationException {
        List<Record> records = new ArrayList<>();
        int lastRow = sheet.getLastRowNum();
        // 忽略仅含样式的尾部空行；数据区内部空行仍需报错定位。
        while (lastRow > 0 && empty(sheet.getRow(lastRow))) lastRow--;
        for (int i = 1; i <= lastRow; i++) {
            XSSFRow row = sheet.getRow(i);
            int originalNumber = i + 1;
            if (row == null || empty(row)) throw new DataValidationException(originalNumber, "记录为空。");
            String admissionDept = department(row, ADMISSION_DEPARTMENT);
            String dischargeDept = department(row, DISCHARGE_DEPARTMENT);
            LocalDateTime admission = ExcelValues.time(row.getCell(ADMISSION_TIME.index()), originalNumber, ADMISSION_TIME);
            LocalDateTime discharge = ExcelValues.time(row.getCell(DISCHARGE_TIME.index()), originalNumber, DISCHARGE_TIME);
            CreatedTime admissionCreated = createdTime(row, ADMISSION_CREATED);
            CreatedTime dischargeCreated = createdTime(row, DISCHARGE_CREATED);
            records.add(new Record(i, admissionDept, dischargeDept, admissionCreated, dischargeCreated,
                    Duration.between(admission, admissionCreated.time()), Duration.between(discharge, dischargeCreated.time())));
            if (i % 100 == 0 || i == lastRow) progress.update(0.05 + 0.38 * i / lastRow, "正在校验第 " + originalNumber + " 行");
        }
        return records;
    }

    private static boolean empty(XSSFRow row) {
        if (row == null) return true;
        for (Cell cell : row) if (!ExcelValues.blank(cell)) return false;
        return true;
    }

    private static String department(XSSFRow row, Column column) throws DataValidationException {
        XSSFCell cell = row.getCell(column.index());
        String value = ExcelValues.text(cell);
        if (value.isBlank() || ExcelValues.type(cell) == CellType.ERROR)
            throw new DataValidationException(row.getRowNum() + 1, column.label() + "为空或不是有效科室名称。");
        return value;
    }

    private static CreatedTime createdTime(XSSFRow row, Column target) throws DataValidationException {
        if (!ExcelValues.blank(row.getCell(target.index()))) {
            return new CreatedTime(ExcelValues.time(row.getCell(target.index()), row.getRowNum() + 1, target), false);
        }
        for (Column fallback : FALLBACK) {
            XSSFCell candidate = row.getCell(fallback.index());
            if (!ExcelValues.blank(candidate)) {
                return new CreatedTime(ExcelValues.time(candidate, row.getRowNum() + 1, fallback), true);
            }
        }
        throw new DataValidationException(row.getRowNum() + 1,
                target.label() + "为空，且 U 至 X 列均无备用时间，无法补全。");
    }

    private static List<DepartmentStatistics> summarize(List<Record> records) {
        Map<String, int[]> totals = new LinkedHashMap<>();
        for (Record record : records) {
            int[] admission = totals.computeIfAbsent(record.admissionDept(), name -> new int[4]);
            admission[0]++;
            if (record.admissionInterval().compareTo(LIMIT) > 0) admission[1]++;
            int[] discharge = totals.computeIfAbsent(record.dischargeDept(), name -> new int[4]);
            discharge[2]++;
            if (record.dischargeInterval().compareTo(LIMIT) > 0) discharge[3]++;
        }
        return totals.entrySet().stream().map(e -> new DepartmentStatistics(e.getKey(),
                e.getValue()[0], e.getValue()[1], e.getValue()[2], e.getValue()[3])).toList();
    }

    private static void applyResults(XSSFWorkbook workbook, XSSFSheet sheet, List<Record> records) {
        sheet.getRow(0).getCell(ADMISSION_INTERVAL.index()).setCellValue("间隔（小时）");
        sheet.getRow(0).getCell(DISCHARGE_INTERVAL.index()).setCellValue("间隔（小时）");
        Map<String, XSSFCellStyle> styles = new HashMap<>();
        for (Record record : records) {
            XSSFRow row = sheet.getRow(record.rowIndex());
            if (record.admissionCreated().filled()) fillTime(row, ADMISSION_CREATED, record.admissionCreated().time());
            if (record.dischargeCreated().filled()) fillTime(row, DISCHARGE_CREATED, record.dischargeCreated().time());
            setInterval(row, ADMISSION_INTERVAL, record.admissionInterval());
            setInterval(row, DISCHARGE_INTERVAL, record.dischargeInterval());
            int width = Math.max(25, row.getLastCellNum());
            for (int c = 0; c < width; c++) {
                XSSFCell cell = row.getCell(c, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
                boolean interval = c == ADMISSION_INTERVAL.index() || c == DISCHARGE_INTERVAL.index();
                boolean late = record.lateCount() > 0;
                if (!interval && !late) continue;
                XSSFCellStyle original = cell.getCellStyle();
                String key = original.getIndex() + ":" + interval + ":" + late;
                cell.setCellStyle(styles.computeIfAbsent(key, ignored -> {
                    XSSFCellStyle style = workbook.createCellStyle();
                    style.cloneStyleFrom(original);
                    if (interval) style.setDataFormat(workbook.createDataFormat().getFormat("0.000000000"));
                    if (late) {
                        style.setFillForegroundColor(IndexedColors.RED.getIndex());
                        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                    }
                    return style;
                }));
            }
        }
        sheet.setColumnWidth(ADMISSION_INTERVAL.index(), Math.max(sheet.getColumnWidth(ADMISSION_INTERVAL.index()), 18 * 256));
        sheet.setColumnWidth(DISCHARGE_INTERVAL.index(), Math.max(sheet.getColumnWidth(DISCHARGE_INTERVAL.index()), 18 * 256));
    }

    private static void fillTime(XSSFRow row, Column column, LocalDateTime time) {
        XSSFCell cell = row.getCell(column.index(), Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
        cell.setBlank();
        cell.setCellValue(time.format(ExcelValues.TIME_FORMAT));
    }

    private static void setInterval(XSSFRow row, Column column, Duration duration) {
        XSSFCell cell = row.getCell(column.index(), Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
        cell.setBlank(); // 清除输入中的共享公式及缓存，按本次时间重新计算小时数。
        cell.setCellValue(duration.getSeconds() / 3600.0 + duration.getNano() / 3_600_000_000_000.0);
    }

    private static void sortRows(XSSFWorkbook workbook, XSSFSheet sheet, List<Record> records) {
        List<Record> sorted = records.stream().sorted(Comparator.comparingInt(Record::lateCount).reversed()).toList();
        XSSFSheet snapshot = workbook.cloneSheet(workbook.getSheetIndex(sheet));
        CellCopyPolicy policy = new CellCopyPolicy.Builder().cellValue(true).cellStyle(true).cellFormula(true)
                .rowHeight(true).mergedRegions(false).build();
        for (int i = 0; i < sorted.size(); i++) {
            XSSFRow target = sheet.createRow(i + 1);
            target.copyRowFrom(snapshot.getRow(sorted.get(i).rowIndex()), policy);
            XSSFCell number = target.getCell(0, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK);
            number.setBlank();
            number.setCellValue(i + 1);
        }
        workbook.removeSheetAt(workbook.getSheetIndex(snapshot));
        workbook.setActiveSheet(0);
    }

    private record CreatedTime(LocalDateTime time, boolean filled) {}
    private record Record(int rowIndex, String admissionDept, String dischargeDept,
                          CreatedTime admissionCreated, CreatedTime dischargeCreated,
                          Duration admissionInterval, Duration dischargeInterval) {
        int lateCount() {
            return (admissionInterval.compareTo(LIMIT) > 0 ? 1 : 0) + (dischargeInterval.compareTo(LIMIT) > 0 ? 1 : 0);
        }
    }
}
