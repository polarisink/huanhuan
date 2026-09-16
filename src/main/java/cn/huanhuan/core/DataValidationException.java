package cn.huanhuan.core;

/** 行号始终指输入工作表的实际行号，而不是 A 列序号。 */
public final class DataValidationException extends Exception {
    private final int rowNumber;

    public DataValidationException(int rowNumber, String reason) {
        super("数据不合法：第 " + rowNumber + " 行，" + reason);
        this.rowNumber = rowNumber;
    }

    public int rowNumber() { return rowNumber; }
}
