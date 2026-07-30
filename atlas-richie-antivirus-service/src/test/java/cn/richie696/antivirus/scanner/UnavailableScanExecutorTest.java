package cn.richie696.antivirus.scanner;

import cn.richie696.antivirus.model.ScanStatus;
import cn.richie696.antivirus.model.ScanTask;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UnavailableScanExecutorTest {

    @Test
    void mustFailClosedWhenNoScannerIsConfigured() {
        ScanOutcome outcome = new UnavailableScanExecutor().scan(new ScanTask());

        assertThat(outcome.status()).isEqualTo(ScanStatus.FAILED);
    }
}
