package cn.richie696.antivirus.scanner;

import cn.richie696.antivirus.model.ScanTask;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/** 未配置实际 ClamAV 执行器时的安全默认值：失败而非放行。 */
@Component
@ConditionalOnMissingBean(ClamdScanExecutor.class)
public class UnavailableScanExecutor implements ScanExecutor {
    @Override
    public ScanOutcome scan(ScanTask task) {
        return ScanOutcome.failed("病毒扫描器尚未配置");
    }
}
