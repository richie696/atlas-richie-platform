package com.richie.component.storage.config;

import jcifs.CIFSContext;
import jcifs.CIFSException;
import jcifs.config.PropertyConfiguration;
import jcifs.context.BaseContext;
import lombok.extern.slf4j.Slf4j;
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
     * @throws CIFSException CIFS异常
     */
    @Bean
    @ConditionalOnProperty(prefix = "platform.component.storage.smb3", name = "enable", havingValue = "true")
    public CIFSContext cifsContext(StorageProperties properties) throws CIFSException {
        var ps = new Properties();
        ps.setProperty("jcifs.smb.client.domain", properties.getSmb3().getDomain());
        ps.setProperty("jcifs.smb.client.username", properties.getSmb3().getUsername());
        ps.setProperty("jcifs.smb.client.password", properties.getSmb3().getPassword());
        ps.setProperty("jcifs.smb.client.dfs.disabled", Boolean.toString(properties.getSmb3().isDfs()));
        ps.setProperty("jcifs.smb.client.dfs.ttl", properties.getSmb3().getDfsTtl().toString());
        ps.setProperty("jcifs.smb.client.dfs.strictView", Boolean.toString(properties.getSmb3().isStrictView()));
        ps.setProperty("jcifs.smb.client.dfs.convertToFQDN", Boolean.toString(properties.getSmb3().isConvertToFQDN()));
        return new BaseContext(new PropertyConfiguration(ps));
    }

}
