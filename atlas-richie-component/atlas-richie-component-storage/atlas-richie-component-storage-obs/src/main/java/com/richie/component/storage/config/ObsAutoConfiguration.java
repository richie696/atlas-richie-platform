package com.richie.component.storage.config;

import com.obs.services.ObsClient;
import com.obs.services.exception.ObsException;
import com.obs.services.model.DeleteObjectRequest;
import com.obs.services.model.PutObjectRequest;
import com.richie.component.storage.bean.ObjectConfig;
import com.richie.component.storage.exception.StorageException;
import com.richie.component.storage.support.ObjectStorageStartupProbe;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

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
public class ObsAutoConfiguration {

    /**
     * 华为云OBS客户端
     *
     * @param properties 存储配置
     * @return 返回华为云OBS客户端
     */
    @Bean
    @ConditionalOnProperty(prefix = "platform.component.storage.object", name = "engine", havingValue = "huawei_obs")
    public ObsClient obsClient(StorageProperties properties) throws StorageException {
        ObjectConfig config = properties.getObject();
        ObsClient obsClient = new ObsClient(config.getAccessKeyId(), config.getAccessKeySecret(), config.getEndpoint());
        if (!config.isAutoCreateBucket()) {
            verifyObsPrefix(obsClient, config);
            return obsClient;
        }
        boolean exists = obsClient.headBucket(config.getBucketName());
        if (!exists) {
            obsClient.createBucket(config.getBucketName());
        }
        return obsClient;
    }

    private void verifyObsPrefix(ObsClient obsClient, ObjectConfig config) throws StorageException {
        String bucket = config.getBucketName();
        String key = ObjectStorageStartupProbe.newProbeObjectKey(config.getBasePath());
        byte[] bytes = ObjectStorageStartupProbe.content();
        try {
            obsClient.putObject(new PutObjectRequest(bucket, key, new ByteArrayInputStream(bytes)));
            var obsObject = obsClient.getObject(bucket, key);
            try (InputStream in = obsObject.getObjectContent()) {
                in.readAllBytes();
            }
        } catch (ObsException e) {
            throw new StorageException("存储前缀读写校验失败: " + e.getErrorMessage(), e);
        } catch (IOException e) {
            throw new StorageException("存储前缀读写校验失败: " + e.getMessage(), e);
        } finally {
            try {
                obsClient.deleteObject(new DeleteObjectRequest(bucket, key));
            } catch (Exception ignored) {
            }
        }
    }

}
