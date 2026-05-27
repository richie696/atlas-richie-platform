package com.richie.component.storage.config;

import com.richie.component.storage.pool.SftpSessionPool;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.core.CoreModuleProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Slf4j
@Configuration
@ComponentScan("com.richie.component.storage")
@EnableConfigurationProperties({StorageProperties.class})
public class SftpAutoConfiguration {

    @Bean(destroyMethod = "stop")
    @ConditionalOnProperty(prefix = "platform.component.storage.sftp", name = "enable", havingValue = "true")
    public SshClient sshClient() {
        var client = SshClient.setUpDefaultClient();
        client.setServerKeyVerifier((_, _, _) -> true);
        CoreModuleProperties.IDLE_TIMEOUT.set(client, Duration.ofMinutes(5));
        client.start();
        log.info("SSHD client started");
        return client;
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(prefix = "platform.component.storage.sftp", name = "enable", havingValue = "true")
    public SftpSessionPool sftpSessionPool(SshClient sshClient, StorageProperties properties) {
        return new SftpSessionPool(sshClient, properties.getSftp());
    }

}
