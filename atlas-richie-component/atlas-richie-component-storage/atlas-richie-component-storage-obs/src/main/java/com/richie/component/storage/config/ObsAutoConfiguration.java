/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.storage.config;

import com.obs.services.ObsClient;
import com.obs.services.exception.ObsException;
import com.obs.services.model.DeleteObjectRequest;
import com.obs.services.model.PutObjectRequest;
import com.richie.component.storage.bean.ObjectConfig;
import com.richie.component.storage.core.StorageEngineProvider;
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
    @ConditionalOnProperty(prefix = "platform.component.storage", name = "auto-init",
            havingValue = "true", matchIfMissing = true)
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

    /**
     * OBS 存储引擎 Provider（手动模式 + 自动模式均注册）
     */
    @Bean
    public StorageEngineProvider obsStorageEngineProvider() {
        return new ObsStorageEngineProvider();
    }

}
