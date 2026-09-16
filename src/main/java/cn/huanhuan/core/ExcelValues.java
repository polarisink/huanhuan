package cn.huanhuan.core;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFCell;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Locale;

final class ExcelValues {
    static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("uuuu/M/d H:m:s", Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT);

    private ExcelValues() {}

    static CellType type(Cell cell) {
        return cell.getCellType() == CellType.FORMULA ? cell.getCachedFormulaResultType() : cell.getCellType();
    }

    static String text(Cell cell) {
        if (cell == null) return "";
        return switch (type(cell)) {
            case STRING -> cell.getStringCellValue().strip();
            case BLANK, _NONE -> "";
            default -> new DataFormatter(Locale.ROOT).formatCellValue(cell).strip();
        };
    }

    static boolean blank(Cell cell) {
        return cell == null || type(cell) == CellType.BLANK
                || (type(cell) == CellType.STRING && cell.getStringCellValue().isBlank());
    }

    static LocalDateTime time(XSSFCell cell, int row, Column column) throws DataValidationException {
        if (blank(cell)) throw new DataValidationException(row, column.label() + "为空。");
        try {
            if (type(cell) == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                double value = cell.getNumericCellValue();
                if (!DateUtil.isValidExcelDate(value)) throw new IllegalArgumentException();
                return DateUtil.getLocalDateTime(value, cell.getSheet().getWorkbook().isDate1904(), true);
            }
            if (type(cell) == CellType.STRING) return LocalDateTime.parse(text(cell), TIME_FORMAT);
        } catch (RuntimeException ignored) {
            // 不在错误中展示患者信息，只提供位置与修正方向。
        }
        throw new DataValidationException(row, column.label() + "时间无法解析，请使用 年/月/日 时:分:秒 格式。");
    }
}
