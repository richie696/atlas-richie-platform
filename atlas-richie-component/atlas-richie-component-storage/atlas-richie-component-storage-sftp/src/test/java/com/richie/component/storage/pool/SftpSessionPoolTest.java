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
