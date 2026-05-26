package com.richie.component.storage.config;

import com.richie.component.storage.bean.FtpConfig;
import com.richie.component.storage.bean.ObjectConfig;
import com.richie.component.storage.enums.StorageEngineEnum;
import cn.hutool.extra.ftp.Ftp;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;

import jakarta.annotation.PostConstruct;

/**
 * 文件存储自动配置类
 *
 * @author richie696
 * @version 1.1
 * @since 2023-09-05 10:39:15
 */
@Slf4j
@Configuration
@ComponentScan("com.richie.component.storage")
@EnableConfigurationProperties({StorageProperties.class})
public class StorageAutoConfiguration {

    private final StorageProperties properties;

    public StorageAutoConfiguration(StorageProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void validateEngineConfig() {
        ObjectConfig objectConfig = properties.getObject();
        if (objectConfig == null || objectConfig.getEngine() == null) {
            return;
        }
        StorageEngineEnum engine = objectConfig.getEngine();
        String configValue = engine.getConfigValue();
        if (StorageEngineEnum.fromConfigValue(configValue) == null) {
            throw new IllegalArgumentException(
                    String.format("无效的对象存储引擎配置值: '%s'。有效值为: [%s]",
                            configValue, StorageEngineEnum.validConfigValues()));
        }
        log.info("对象存储引擎已配置为: {} ({})", configValue, engine.getDescription());
    }

    /**
     * FTP 客户端
     *
     * @param properties 存储配置
     * @return 返回FTP客户端
     */
    @Bean
    @Scope(BeanDefinition.SCOPE_PROTOTYPE)
    @ConditionalOnProperty(prefix = "platform.component.storage.ftp", name = "enable", havingValue = "true")
    public Ftp ftp(StorageProperties properties) {
        FtpConfig config = properties.getFtp();
        Ftp ftp = new Ftp(config.getHost(), config.getPort(), config.getUsername(),
                config.getPassword(), config.getCharset(), config.getServerLanguageCode(),
                config.getSystemKey().getValue(), config.getFtpMode());
        if (!ftp.exist(config.getBasePath())) {
            ftp.cd(config.getBasePath());
        }
        return ftp;
    }

}
