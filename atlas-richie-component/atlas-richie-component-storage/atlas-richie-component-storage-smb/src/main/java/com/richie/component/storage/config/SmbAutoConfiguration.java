/*
 * Copyright (c) 2026 Richie (https://www.github.com/richie696)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.richie.component.storage.config;

import com.richie.component.storage.core.StorageEngineProvider;
import lombok.extern.slf4j.Slf4j;
import org.codelibs.jcifs.smb.CIFSContext;
import org.codelibs.jcifs.smb.CIFSException;
import org.codelibs.jcifs.smb.config.PropertyConfiguration;
import org.codelibs.jcifs.smb.context.BaseContext;
import org.codelibs.jcifs.smb.impl.NtlmPasswordAuthenticator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.util.Properties;

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
public class SmbAutoConfiguration {

    /**
     * SMB CIFS上下文
     *
     * @param properties 存储配置
     * @return 返回CIFS上下文
     */
    @Bean
    @ConditionalOnProperty(prefix = "platform.component.storage.smb3", name = "enable", havingValue = "true")
    @ConditionalOnProperty(prefix = "platform.component.storage", name = "auto-init",
            havingValue = "true", matchIfMissing = true)
    public CIFSContext cifsContext(StorageProperties properties) throws CIFSException {
        var smb3 = properties.getSmb3();
        var ps = new Properties();
        ps.setProperty("jcifs.smb.client.dfs.disabled", Boolean.toString(smb3.isDfs()));
        ps.setProperty("jcifs.smb.client.dfs.ttl", smb3.getDfsTtl().toString());
        ps.setProperty("jcifs.smb.client.dfs.strictView", Boolean.toString(smb3.isStrictView()));
        ps.setProperty("jcifs.smb.client.dfs.convertToFQDN", Boolean.toString(smb3.isConvertToFQDN()));
        var baseContext = new BaseContext(new PropertyConfiguration(ps));
        var auth = new NtlmPasswordAuthenticator("WORKGROUP", smb3.getUsername(), smb3.getPassword());
        return baseContext.withCredentials(auth);
    }

    /**
     * SMB 存储引擎 Provider（手动模式 + 自动模式均注册）
     */
    @Bean
    public StorageEngineProvider smbStorageEngineProvider() {
        return new SmbStorageEngineProvider();
    }

}
