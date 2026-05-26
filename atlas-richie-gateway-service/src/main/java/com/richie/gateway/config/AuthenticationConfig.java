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
