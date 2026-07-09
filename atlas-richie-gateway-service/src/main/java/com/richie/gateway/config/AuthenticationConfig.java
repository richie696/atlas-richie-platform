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
package com.richie.gateway.config;

import com.richie.gateway.enums.EncryptTypeEnum;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Configuration;

/**
 * 接口鉴权配置类
 *
 * @author richie696
 * @version 1.0
 * @since 2023-09-25 10:37:56
 */
@Data
@Configuration
@RefreshScope
@NoArgsConstructor
@ConfigurationProperties(prefix = "platform.gateway.security.authentication")
public class AuthenticationConfig {

    /**
     * 鉴权秘钥
     */
    private String secretKey;

    /**
     * 签名方式
     */
    private EncryptTypeEnum encryptType = EncryptTypeEnum.SM2;

}
