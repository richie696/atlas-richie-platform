package cn.richie696.antivirus.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "platform.antivirus")
public class AntivirusProperties {
    /** Redis 查询记录的保留时长；到期后调用方须重新提交。 */
    private Duration taskTtl = Duration.ofHours(72);
    private String taskKeyPrefix = "antivirus:scan:task:";
    private String taskStream = "antivirus-scan-requests";
    private Recovery recovery = new Recovery();
    private Clamav clamav = new Clamav();
    private Download download = new Download();
    private Local local = new Local();
    private Grpc grpc = new Grpc();

    @Data
    public static class Recovery {
        /** 单次扫描的执行租约；超时后允许其他实例重新领取任务。 */
        private Duration leaseDuration = Duration.ofMinutes(10);
        /** 消费异常且尚未取得租约时，延迟多久重新投递。 */
        private Duration retryDelay = Duration.ofSeconds(10);
        private String leaseKeyPrefix = "antivirus:scan:lease:";
        private String scheduleKey = "antivirus:scan:recovery";
        private long pollIntervalMs = 10_000L;
        private int batchSize = 100;
    }

    @Data
    public static class Clamav {
        private boolean enabled;
        private String socketPath = "/tmp/clamd.sock";
        private long maxFileSizeBytes = 209_715_200L;
        private int mimeProbeBytes = 65_536;
    }

    @Data
    public static class Download {
        private boolean allowHttp;
        private Duration connectTimeout = Duration.ofSeconds(5);
        private Duration requestTimeout = Duration.ofMinutes(3);
        private int maxRedirects = 3;
    }

    @Data
    public static class Local {
        /** 默认 false；启用后 /internal/v1/scans/local 才会接受请求。 */
        private boolean enabled;
        /** 路径必须 resolve 到这些目录之一；防止任意文件读取。 */
        private List<String> allowedPaths = List.of();
    }

    @Data
    public static class Grpc {
        /** 默认 false；启用后 gRPC AntivirusService 才会监听 port。 */
        private boolean enabled;
        /** gRPC 监听端口；只有 enabled=true 才生效。 */
        private int port = 9601;
        /** Nacos 服务名；用于独立注册 gRPC 实例。 */
        private String nacosServiceName = "platform-antivirus-service-grpc";
    }
}
