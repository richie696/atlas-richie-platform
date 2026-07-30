package cn.richie696.antivirus.scanner;

import cn.richie696.antivirus.config.AntivirusProperties;
import cn.richie696.antivirus.model.ScanStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 连接真实 clamd Unix Socket 的可选集成测试。
 * EICAR 是杀毒软件行业提供的无害检测测试载荷，不包含可执行恶意代码。
 */
@EnabledIfEnvironmentVariable(named = "ANTIVIRUS_EICAR_IT", matches = "(?i)true")
class ClamdEicarIntegrationTest {
    private static final String EICAR =
            "X5O!P%@AP[4\\PZX54(P^)7CC)7}$EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*";

    @Test
    void detectsEicarThroughUnixSocket() throws Exception {
        AntivirusProperties properties = new AntivirusProperties();
        String socketPath = System.getenv("ANTIVIRUS_CLAMAV_SOCKET_PATH");
        if (socketPath != null && !socketPath.isBlank()) {
            properties.getClamav().setSocketPath(socketPath);
        }
        ClamdClient client = new ClamdClient(properties, new TikaMimeDetector());

        ScanOutcome outcome = client.scan(
                new ByteArrayInputStream(EICAR.getBytes(StandardCharsets.US_ASCII)),
                "eicar.com");

        assertThat(outcome.status()).isEqualTo(ScanStatus.INFECTED);
        assertThat(outcome.threatName()).containsIgnoringCase("Eicar");
        assertThat(outcome.sha256())
                .isEqualTo("275a021bbfb6489e54d471899f7db9d1663fc695ec2fe2a2c4538aabf651fd0f");
    }
}
