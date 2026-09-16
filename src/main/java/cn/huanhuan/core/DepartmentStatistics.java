package cn.huanhuan.core;

public record DepartmentStatistics(String name, int admissionTotal, int admissionFailed,
                                   int dischargeTotal, int dischargeFailed) {
    public double admissionRate() {
        if (admissionTotal == 0) throw new IllegalStateException("入院无数据");
        return (admissionTotal - admissionFailed) / (double) admissionTotal;
    }
    public double dischargeRate() {
        if (dischargeTotal == 0) throw new IllegalStateException("出院无数据");
        return (dischargeTotal - dischargeFailed) / (double) dischargeTotal;
    }
}
