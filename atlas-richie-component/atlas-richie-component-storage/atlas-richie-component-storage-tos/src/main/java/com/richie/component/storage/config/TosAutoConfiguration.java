package com.richie.component.storage.config;

import com.richie.component.storage.bean.ObjectConfig;
import com.richie.component.storage.exception.StorageException;
import com.richie.component.storage.support.ObjectStorageStartupProbe;
import com.volcengine.tos.*;
import com.volcengine.tos.credential.StaticCredentialsProvider;
import com.volcengine.tos.model.bucket.HeadBucketV2Input;
import com.volcengine.tos.model.bucket.HeadBucketV2Output;
import com.volcengine.tos.model.object.DeleteObjectInput;
import com.volcengine.tos.model.object.GetObjectV2Input;
import com.volcengine.tos.model.object.PutObjectInput;
import com.volcengine.tos.transport.TransportConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * 文件存储自动配置类
 *
 * @author richie696
 * @version 1.0
 * @since 2023-09-05 10:39:15
 */
@Slf4j
@Configuration
@ComponentScan("com.richie.component.storage")
@EnableConfigurationProperties({StorageProperties.class})
public class TosAutoConfiguration {

    /**
     * 火山引擎 TOS 客户端
     *
     * @param properties 存储配置
     * @return 返回火山引擎 TOS 客户端
     */
    @Bean
    @ConditionalOnProperty(prefix = "platform.component.storage.object", name = "engine", havingValue = "volcengine_tos")
    public TOSV2 tosClient(StorageProperties properties) throws StorageException {
        ObjectConfig config = properties.getObject();
        int connectTimeoutMills = 10000;

        TransportConfig transportConfig = TransportConfig.builder()
                .connectTimeoutMills(connectTimeoutMills)
                .build();
        TOSClientConfiguration configuration = TOSClientConfiguration.builder()
                .transportConfig(transportConfig)
                .region(config.getRegion())
                .endpoint(config.getEndpoint())
                .credentialsProvider(new StaticCredentialsProvider(config.getAccessKeyId(), config.getAccessKeySecret()))
                .build();

        TOSV2 tos = new TOSV2ClientBuilder().build(configuration);
        if (!config.isAutoCreateBucket()) {
            verifyTosPrefix(tos, config);
            return tos;
        }
        HeadBucketV2Input input = HeadBucketV2Input.builder().bucket(config.getBucketName()).build();
        HeadBucketV2Output output = tos.headBucket(input);
        switch (output.getRequestInfo().getStatusCode()) {
            case 200 -> {
            }
            case 404 -> tos.createBucket(config.getBucketName());
            default -> throw new StorageException("当前桶无访问权限");
        }
        return tos;
    }

    private void verifyTosPrefix(TOSV2 tos, ObjectConfig config) throws StorageException {
        String bucket = config.getBucketName();
        String key = ObjectStorageStartupProbe.newProbeObjectKey(config.getBasePath());
        byte[] bytes = ObjectStorageStartupProbe.content();
        try {
            tos.putObject(new PutObjectInput().setBucket(bucket).setKey(key).setContent(new ByteArrayInputStream(bytes)));
            try (var output = tos.getObject(new GetObjectV2Input().setBucket(bucket).setKey(key))) {
                output.getContent().readAllBytes();
            }
        } catch (TosClientException | TosServerException | IOException e) {
            throw new StorageException("存储前缀读写校验失败: " + e.getMessage(), e);
        } finally {
            try {
                tos.deleteObject(new DeleteObjectInput().setBucket(bucket).setKey(key));
            } catch (Exception ignored) {
            }
        }
    }

}
