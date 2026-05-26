package com.richie.component.storage.config;

import com.richie.component.storage.bean.ObjectConfig;
import com.richie.component.storage.exception.StorageException;
import com.richie.component.storage.support.ObjectStorageStartupProbe;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.common.auth.CredentialsProviderFactory;
import com.aliyun.oss.common.auth.DefaultCredentialProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import java.io.ByteArrayInputStream;
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
public class OssAutoConfiguration {

    /**
     * 阿里云OSS客户端
     *
     * @param properties 存储配置
     * @return 返回阿里云OSS客户端
     */
    @Bean
    @Scope(BeanDefinition.SCOPE_PROTOTYPE)
    @ConditionalOnProperty(prefix = "platform.component.storage.object", name = "engine", havingValue = "aliyun_oss")
    public OSS ossClient(StorageProperties properties) throws StorageException {
        ObjectConfig config = properties.getObject();
        DefaultCredentialProvider credentialProvider = CredentialsProviderFactory
                .newDefaultCredentialProvider(config.getAccessKeyId(), config.getAccessKeySecret());
        OSS ossClient = new OSSClientBuilder().build(config.getEndpoint(), credentialProvider);
        if (!config.isAutoCreateBucket()) {
            verifyOssPrefix(ossClient, config);
            return ossClient;
        }
        boolean exists = ossClient.doesBucketExist(config.getBucketName());
        if (!exists) {
            ossClient.createBucket(config.getBucketName());
        }
        return ossClient;
    }

    private void verifyOssPrefix(OSS ossClient, ObjectConfig config) throws StorageException {
        String bucket = config.getBucketName();
        String key = ObjectStorageStartupProbe.newProbeObjectKey(config.getBasePath());
        byte[] bytes = ObjectStorageStartupProbe.content();
        try {
            ossClient.putObject(bucket, key, new ByteArrayInputStream(bytes));
            var ossObject = ossClient.getObject(bucket, key);
            try (InputStream in = ossObject.getObjectContent()) {
                in.readAllBytes();
            }
        } catch (Exception e) {
            throw new StorageException("存储前缀读写校验失败: " + e.getMessage(), e);
        } finally {
            try {
                ossClient.deleteObject(bucket, key);
            } catch (Exception ignored) {
            }
        }
    }

}
