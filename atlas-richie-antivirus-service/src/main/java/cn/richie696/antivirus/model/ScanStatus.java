package cn.richie696.antivirus.model;

/** 扫描任务状态；只有 CLEAN 能使业务方放行文件。 */
public enum ScanStatus {
    PENDING,
    SCANNING,
    CLEAN,
    INFECTED,
    FAILED
}
