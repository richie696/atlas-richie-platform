package com.richie.gateway.config;

import com.richie.gateway.handler.KeyPairManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration(proxyBeanMethods = false)
public class GatewayAutoConfiguration {

    /**
     * 初始化网关密钥管理器
     *
     * @return 网关密钥管理器对象
     */
     @Bean
     public KeyPairManager keyPairManager(GatewayConfig config) {
         return new KeyPairManager(config);
     }
}

