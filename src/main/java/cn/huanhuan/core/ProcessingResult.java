package cn.huanhuan.core;

import java.nio.file.Path;
import java.util.List;

public record ProcessingResult(Path outputFile, int recordCount,
                               int lateRecordCount, List<DepartmentStatistics> departments) {
    public ProcessingResult { departments = List.copyOf(departments); }
}
