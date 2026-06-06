package com.richie.component.storage.pool;

import com.richie.component.storage.bean.SftpConfig;
import org.apache.sshd.client.SshClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SftpSessionPoolTest {

    private SshClient sshClient;

    @AfterEach
    void tearDown() {
        if (sshClient != null && sshClient.isStarted()) {
            sshClient.stop();
        }
    }

    @Test
    void pool_canBeConstructedWithClientAndConfig() {
        sshClient = SshClient.setUpDefaultClient();
        sshClient.start();
        SftpConfig config = new SftpConfig();
        config.setHost("sftp.example.com");
        config.setPort(22);
        config.setUsername("user");
        config.setPassword("secret");
        config.setBasePath("/uploads");
        config.setMaxTotal(4);

        assertThat(new SftpSessionPool(sshClient, config)).isNotNull();
    }
}
