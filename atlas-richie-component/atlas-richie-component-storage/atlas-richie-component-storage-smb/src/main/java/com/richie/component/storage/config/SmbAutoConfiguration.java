package com.richie.component.storage.config;

import org.codelibs.jcifs.smb.CIFSContext;
import org.codelibs.jcifs.smb.CIFSException;
import org.codelibs.jcifs.smb.config.PropertyConfiguration;
import org.codelibs.jcifs.smb.context.BaseContext;
import org.codelibs.jcifs.smb.context.SingletonContext;
import org.codelibs.jcifs.smb.impl.NtlmPasswordAuthenticator;
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
     */
    @Bean
    @ConditionalOnProperty(prefix = "platform.component.storage.smb3", name = "enable", havingValue = "true")
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

}
