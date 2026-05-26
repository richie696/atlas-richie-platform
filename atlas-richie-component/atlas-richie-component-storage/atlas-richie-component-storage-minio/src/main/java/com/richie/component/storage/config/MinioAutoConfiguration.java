package com.richie.component.storage.config;

import com.richie.component.storage.bean.ObjectConfig;
import com.richie.component.storage.exception.StorageException;
import com.richie.component.storage.support.ObjectStorageStartupProbe;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioAsyncClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.InsufficientDataException;
import io.minio.errors.InternalException;
import io.minio.errors.XmlParserException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

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
public class MinioAutoConfiguration {

    /**
     * Minio 客户端
     *
     * @param properties 存储配置
     * @return 返回Minio客户端
     * @throws InsufficientDataException 当数据长度不足时抛出该异常（在读取完成之前收到EOF）
     * @throws IOException               当发生I/O错误时抛出该异常
     * @throws NoSuchAlgorithmException  当算法不可用时抛出该异常
     * @throws InvalidKeyException       当提供的 Minio 密钥无效时抛出该异常
     * @throws XmlParserException        当判断 Bucket 桶是否存在时，出现解析XML时发生错误时抛出该异常
     * @throws InternalException         当判断 Bucket 桶是否存在时，发生内部错误时抛出该异常
     * @throws ExecutionException        当创建 Bucket 桶执行失败时抛出该异常
     * @throws InterruptedException      当创建 Bucket 桶线程被中断时抛出该异常
     * @throws TimeoutException          当创建 Bucket 桶超时时抛出该异常
     */
    @Bean
    @Scope(BeanDefinition.SCOPE_PROTOTYPE)
    @ConditionalOnProperty(prefix = "platform.component.storage.object", name = "engine", havingValue = "minio")
    public MinioAsyncClient minioAsyncClient(StorageProperties properties) throws InsufficientDataException,
            IOException, NoSuchAlgorithmException, InvalidKeyException, XmlParserException, InternalException,
            ExecutionException, InterruptedException, TimeoutException, StorageException {
        ObjectConfig config = properties.getObject();
        MinioAsyncClient minioAsyncClient = MinioAsyncClient.builder()
                .endpoint(config.getEndpoint())
                .credentials(config.getAccessKeyId(), config.getAccessKeySecret())
                .region(config.getRegion())
                .build();
        if (!config.isAutoCreateBucket()) {
            verifyMinioPrefix(minioAsyncClient, config);
            return minioAsyncClient;
        }
        BucketExistsArgs bucketExistsArgs = BucketExistsArgs.builder().bucket(config.getBucketName()).build();
        Boolean exists = minioAsyncClient.bucketExists(bucketExistsArgs).get(10, TimeUnit.SECONDS);
        if (!exists) {
            MakeBucketArgs makeBucketArgs = MakeBucketArgs.builder().bucket(config.getBucketName()).build();
            minioAsyncClient.makeBucket(makeBucketArgs);
        }
        return minioAsyncClient;
    }

    private void verifyMinioPrefix(MinioAsyncClient client, ObjectConfig config) throws StorageException {
        String bucket = config.getBucketName();
        String key = ObjectStorageStartupProbe.newProbeObjectKey(config.getBasePath());
        byte[] bytes = ObjectStorageStartupProbe.content();
        try {
            client.putObject(
                            PutObjectArgs.builder().bucket(bucket).object(key)
                                    .stream(new ByteArrayInputStream(bytes), bytes.length, -1)
                                    .build())
                    .get(30, TimeUnit.SECONDS);
            try (var is = client.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build())
                    .get(30, TimeUnit.SECONDS)) {
                is.readAllBytes();
            }
        } catch (Exception e) {
            throw new StorageException("存储前缀读写校验失败: " + e.getMessage(), e);
        } finally {
            try {
                client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build())
                        .get(30, TimeUnit.SECONDS);
            } catch (Exception ignored) {
            }
        }
    }

}
