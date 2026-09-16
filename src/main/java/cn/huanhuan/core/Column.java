package cn.huanhuan.core;

public enum Column {
    DISCHARGE_TIME(8, "出院时间"), ADMISSION_TIME(9, "入院时间"),
    DISCHARGE_DEPARTMENT(12, "出院科室"), ADMISSION_DEPARTMENT(13, "入院科室"),
    ADMISSION_CREATED(16, "入院记录创建时间"), ADMISSION_INTERVAL(17, "间隔"),
    DISCHARGE_CREATED(18, "出院记录创建时间"), DISCHARGE_INTERVAL(19, "间隔"),
    SHORT_STAY(20, "24小时入出院记录时间"), SHORT_STAY_DEATH(21, "24小时入院死亡记录时间"),
    DISCHARGE_SUMMARY(22, "出院小结记录时间"), DEATH_RECORD(23, "死亡记录时间");

    private final int index;
    private final String title;
    Column(int index, String title) { this.index = index; this.title = title; }
    public int index() { return index; }
    public String title() { return title; }
    public String label() { return (char) ('A' + index) + " 列“" + title + "”"; }
}
