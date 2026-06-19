package com.richie.component.storage.config;

import com.richie.context.common.api.SpringContextHolder;
import io.minio.BucketExistsArgs;
import io.minio.MinioAsyncClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Minio 自动配置集成测试 —— 使用 Testcontainers 启动真实 MinIO 容器。
 * <p>
 * 验证 {@link MinioAutoConfiguration#minioAsyncClient(StorageProperties)} 在真实 MinIO 服务下的行为，
 * 包括客户端创建和桶自动创建。
 */
@SpringBootTest(classes = {
        MinioAutoConfiguration.class,
        SpringContextHolder.class
})
@Testcontainers
@EnabledIf("isDockerAvailable")
class MinioAutoConfigurationIT {

    private static final String BUCKET = "it-autoconfig-bucket";
    private static final String ACCESS_KEY = "minioadmin";
    private static final String SECRET_KEY = "minioadmin";

    static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    @Container
    static GenericContainer<?> minioContainer = new GenericContainer<>("minio/minio:latest")
            .withCommand("server /data")
            .withExposedPorts(9000)
            .withEnv("MINIO_ROOT_USER", ACCESS_KEY)
            .withEnv("MINIO_ROOT_PASSWORD", SECRET_KEY);

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("platform.component.storage.object.endpoint",
                () -> "http://127.0.0.1:" + minioContainer.getMappedPort(9000));
        registry.add("platform.component.storage.object.access-key-id", () -> ACCESS_KEY);
        registry.add("platform.component.storage.object.access-key-secret", () -> SECRET_KEY);
        registry.add("platform.component.storage.object.bucket-name", () -> BUCKET);
        registry.add("platform.component.storage.object.engine", () -> "minio");
        registry.add("platform.component.storage.object.region", () -> "us-east-1");
    }

    @Autowired
    private MinioAsyncClient minioAsyncClient;

    @Test
    void minioAsyncClient_shouldBeCreated() {
        assertThat(minioAsyncClient).isNotNull();
    }

    @Test
    void bucket_shouldBeAutoCreated() throws Exception {
        boolean exists = minioAsyncClient.bucketExists(
                BucketExistsArgs.builder().bucket(BUCKET).build()
        ).get(15, TimeUnit.SECONDS);
        assertThat(exists).isTrue();
    }
}
