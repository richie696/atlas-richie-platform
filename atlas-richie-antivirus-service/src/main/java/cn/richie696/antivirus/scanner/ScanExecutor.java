package cn.richie696.antivirus.scanner;

import cn.richie696.antivirus.model.ScanTask;

/**
 * 扫描执行边界。公共 URL 下载和 ClamAV 调用只在本服务中实现，
 * 不向 HTTP 等原子组件反向注入扫描业务逻辑。
 */
public interface ScanExecutor {
    ScanOutcome scan(ScanTask task);
}
