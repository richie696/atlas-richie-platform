package com.richie.component.storage.config;

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * S3 自动配置集成测试 — 使用 RustFS 作为 S3 兼容后端。
 * <p>
 * RustFS 是 100% S3 兼容的高性能对象存储系统，可作为 MinIO 的替代方案。
 * 此测试验证在 RustFS 后端下 S3AutoConfiguration 的 Bean 创建和存储桶自动创建功能。
 */
@Slf4j
@Testcontainers
@EnabledIf("isDockerAvailable")
@SpringBootTest(
    classes = S3AutoConfiguration.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE
)
@TestPropertySource(properties = {
    "platform.component.storage.object.engine=aws_s3",
    "platform.component.storage.object.endpoint=http://127.0.0.1:${rustfs.port}",
    "platform.component.storage.object.access-key-id=rustfsadmin",
    "platform.component.storage.object.access-key-secret=rustfsadmin",
    "platform.component.storage.object.bucket-name=rustfs-test-bucket",
    "platform.component.storage.object.region=us-east-1",
    "platform.component.storage.object.base-path=test",
    "platform.component.storage.object.auto-create-bucket=true"
})
class S3AutoConfigurationRustFSIT {

    @Container
    static GenericContainer<?> rustfsContainer = new GenericContainer<>("rustfs/rustfs:latest")
            .withCommand("/data")
            .withExposedPorts(9000)
            .withEnv("RUSTFS_ACCESS_KEY", "rustfsadmin")
            .withEnv("RUSTFS_SECRET_KEY", "rustfsadmin");

    static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable t) {
            return false;
        }
    }

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        registry.add("platform.component.storage.object.endpoint",
            () -> "http://127.0.0.1:" + rustfsContainer.getMappedPort(9000));
    }

    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void s3ClientBean_shouldBeCreated() {
        S3Client client = applicationContext.getBean(S3Client.class);
        assertThat(client).isNotNull();
    }

    @Test
    void s3PresignerBean_shouldBeCreated() {
        S3Presigner presigner = applicationContext.getBean(S3Presigner.class);
        assertThat(presigner).isNotNull();
    }

    @Test
    void bucket_shouldBeAutoCreated() throws Exception {
        S3Client client = applicationContext.getBean(S3Client.class);
        HeadBucketResponse response = client.headBucket(HeadBucketRequest.builder()
                .bucket("rustfs-test-bucket")
                .build());
        assertThat(response).isNotNull();
    }
}
