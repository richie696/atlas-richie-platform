package com.richie.component.storage.config;

import com.richie.component.storage.bean.SftpConfig;
import com.richie.component.storage.config.StorageProperties;
import com.richie.component.storage.core.StorageEngine;
import com.richie.component.storage.core.StorageEngineProvider;
import com.richie.component.storage.core.impl.SftpStorageEngine;
import com.richie.component.storage.enums.StorageEngineEnum;
import com.richie.component.storage.pool.SftpSessionPool;
import lombok.extern.slf4j.Slf4j;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.core.CoreModuleProperties;

import java.time.Duration;

@Slf4j
public class SftpStorageEngineProvider implements StorageEngineProvider {

    private SshClient sshClient;

    @Override
    public StorageEngineEnum supportedEngineType() {
        return StorageEngineEnum.SFTP;
    }

    @Override
    public StorageEngine create(StorageProperties properties) {
        SftpConfig sftpConfig = properties.getSftp();
        SshClient client = SshClient.setUpDefaultClient();
        client.setServerKeyVerifier((_, _, _) -> true);
        CoreModuleProperties.IDLE_TIMEOUT.set(client, Duration.ofMinutes(5));
        client.start();
        this.sshClient = client;
        SftpSessionPool pool = new SftpSessionPool(client, sftpConfig);
        return new SftpStorageEngine(properties, pool);
    }

    @Override
    public void destroy(StorageEngine engine) {
        if (sshClient != null) {
            sshClient.stop();
            log.info("SFTP SSH client stopped");
        }
    }

    @Override
    public void validate(StorageProperties properties) {
        SftpConfig c = properties.getSftp();
        ConfigValidation.requireNonNull(c, "SFTP 配置");
        ConfigValidation.requireNonBlank(c.getHost(), "SFTP host");
        ConfigValidation.requireNonBlank(c.getUsername(), "SFTP username");
        ConfigValidation.requireNonBlank(c.getPassword(), "SFTP password");
    }
}
